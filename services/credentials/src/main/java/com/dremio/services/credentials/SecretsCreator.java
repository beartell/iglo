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
package com.dremio.services.credentials;

import java.net.URI;

/** Primarily interface for in-line encryption of secrets. */
public interface SecretsCreator {

  /** Validate and return true if the secret has already been encrypted. */
  boolean isEncrypted(String secret);

  /**
   * Encrypt the given secret in-line, returning the encrypted value as a CredentialsService URI.
   */
  public URI encrypt(String secret) throws CredentialsException;
}
