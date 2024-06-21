/*
 * Copyright (C) 2017-2019 Dremio Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dremio.exec.store.sys.udf;

import com.dremio.common.exceptions.UserException;
import com.dremio.config.DremioConfig;
import com.dremio.exec.proto.CoordinationProtos;
import com.dremio.exec.proto.FunctionRPC;
import com.dremio.service.namespace.NamespaceService;
import com.dremio.service.namespace.function.proto.FunctionConfig;
import com.dremio.services.fabric.api.FabricRunnerFactory;
import com.dremio.services.fabric.api.FabricService;
import com.google.common.base.Preconditions;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import javax.inject.Provider;
import org.apache.arrow.memory.BufferAllocator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** UserDefinedFunctionListManagerImpl */
public class UserDefinedFunctionServiceImpl implements UserDefinedFunctionService {
  public static final Logger logger = LoggerFactory.getLogger(UserDefinedFunctionServiceImpl.class);
  protected final Provider<NamespaceService> namespaceServiceProvider;
  protected final Provider<Optional<CoordinationProtos.NodeEndpoint>> serviceLeaderProvider;
  protected final Provider<FabricService> fabric;
  protected final Provider<BufferAllocator> allocatorProvider;
  protected final Provider<DremioConfig> dremioConfigProvider;
  protected final boolean isMaster;
  protected final boolean isCoordinator;
  protected FunctionTunnelCreator functionTunnelCreator;

  public UserDefinedFunctionServiceImpl(
      Provider<NamespaceService> namespaceServiceProvider,
      Provider<Optional<CoordinationProtos.NodeEndpoint>> serviceLeaderProvider,
      final Provider<FabricService> fabric,
      Provider<BufferAllocator> allocatorProvider,
      Provider<DremioConfig> dremioConfigProvider,
      boolean isMaster,
      boolean isCoordinator) {
    this.namespaceServiceProvider =
        Preconditions.checkNotNull(namespaceServiceProvider, "NamespaceService service required");
    this.allocatorProvider =
        Preconditions.checkNotNull(allocatorProvider, "buffer allocator required");
    this.fabric = Preconditions.checkNotNull(fabric, "fabric service required");
    this.serviceLeaderProvider =
        Preconditions.checkNotNull(serviceLeaderProvider, "serviceLeaderProvider required");
    this.dremioConfigProvider =
        Preconditions.checkNotNull(dremioConfigProvider, "dremioConfigProvider required");
    this.isMaster = isMaster;
    this.isCoordinator = isCoordinator;
  }

  @Override
  public Iterable<FunctionInfo> functionInfos() {
    if (isMaster || (isCoordinator && dremioConfigProvider.get().isMasterlessEnabled())) {
      return namespaceServiceProvider.get().getFunctions().stream()
          .map(
              functionConfig -> {
                FunctionInfo functionInfo =
                    UserDefinedFunctionService.getFunctionInfoFromConfig(functionConfig);
                functionInfo.setOwner(getOwnerNameFromFunctionConfig(functionConfig));
                return functionInfo;
              })
          .collect(Collectors.toList());
    }
    Optional<CoordinationProtos.NodeEndpoint> master = serviceLeaderProvider.get();
    if (!master.isPresent()) {
      throw UserException.connectionError()
          .message("Unable to get task leader while trying to get function information")
          .build(logger);
    }
    final FunctionTunnel udfTunnel = functionTunnelCreator.getTunnel(master.get());
    try {
      final FunctionRPC.FunctionInfoResp udfInfoResp =
          udfTunnel.requestFunctionInfos().get(15, TimeUnit.SECONDS);
      return udfInfoResp.getFunctionInfoList().stream()
          .map(UserDefinedFunctionService.FunctionInfo::fromProto)
          .collect(Collectors.toList());
    } catch (InterruptedException | ExecutionException | TimeoutException e) {
      throw UserException.connectionError(e)
          .message("Error while getting function information")
          .build(logger);
    }
  }

  @Override
  public void start() throws Exception {
    final FabricRunnerFactory udfTunnelFactory =
        fabric
            .get()
            .registerProtocol(
                new FunctionProtocol(
                    allocatorProvider.get(),
                    dremioConfigProvider.get().getSabotConfig(),
                    namespaceServiceProvider));
    functionTunnelCreator = new FunctionTunnelCreator(udfTunnelFactory);
  }

  @Override
  public void close() throws Exception {}

  protected String getOwnerNameFromFunctionConfig(FunctionConfig functionConfig) {
    return null;
  }

  /** UdfTunnelCreator */
  public static class FunctionTunnelCreator {
    private final FabricRunnerFactory factory;

    public FunctionTunnelCreator(FabricRunnerFactory factory) {
      super();
      this.factory = factory;
    }

    public FunctionTunnel getTunnel(CoordinationProtos.NodeEndpoint ep) {
      return new FunctionTunnel(ep, factory.getCommandRunner(ep.getAddress(), ep.getFabricPort()));
    }
  }
}
