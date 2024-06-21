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
import { shallow } from "enzyme";
import Immutable from "immutable";

import { SQLEditor } from "./SQLEditor";

describe("SQLEditor", () => {
  let minimalProps;
  let commonProps;
  let wrapper;
  let instance;

  const stubMonacoEditorComponent = () => {
    instance.monacoEditorComponent = {
      editor: {
        setValue: sinon.stub(),
        deltaDecorations: sinon.stub(),
        focus: sinon.stub(),
        getModel: sinon.stub(),
        executeEdits: sinon.stub(),
        setSelection: sinon.stub(),
      },
    };
  };

  const stubMonaco = () => {
    instance.monaco = {
      Range: () => {
        return { isEmpty: sinon.stub() };
      },
      editor: {
        TrackedRangeStickiness: { NeverGrowsWhenTypingAtEdges: false },
        OverviewRulerLane: {}, // an enum
      },
    };
  };

  beforeEach(() => {
    minimalProps = {
      height: 300,
    };
    commonProps = {
      ...minimalProps,
      defaultValue: "the default value",
      onChange: sinon.spy(),
    };

    wrapper = shallow(<SQLEditor {...commonProps} />);
    instance = wrapper.instance();
  });

  it("should render with minimal props without exploding", () => {
    wrapper = shallow(<SQLEditor {...minimalProps} />);
    expect(wrapper).to.have.length(1);
  });

  describe("#componentDidMount", () => {
    it("should set default value only if it !== undefined", () => {
      sinon.stub(instance, "resetValue");
      instance.componentDidMount();
      expect(instance.resetValue).to.be.called;
      instance.resetValue.resetHistory();

      wrapper = shallow(
        <SQLEditor {...commonProps} defaultValue={undefined} />
      );
      instance = wrapper.instance();
      sinon.stub(instance, "resetValue");
      instance.componentDidMount();
      expect(instance.resetValue).to.not.be.called;

      wrapper = shallow(<SQLEditor {...commonProps} defaultValue={""} />);
      instance = wrapper.instance();
      sinon.stub(instance, "resetValue");
      instance.componentDidMount();
      expect(instance.resetValue).to.be.called;
    });
  });

  describe("#componentDidUpdate", () => {
    it("should resetValue only if defaultValue has changed", () => {
      sinon.stub(instance, "resetValue");
      instance.componentDidMount();
      instance.resetValue.resetHistory();

      instance.componentDidUpdate(commonProps);
      expect(instance.resetValue).to.not.be.called;

      instance.componentDidUpdate({
        ...commonProps,
        defaultValue: "different value",
      });
      expect(instance.resetValue).to.be.called;

      wrapper.setProps({ defaultValue: undefined });
      instance.componentDidUpdate({
        ...commonProps,
        defaultValue: "different value",
      });
      expect(instance.resetValue).to.be.calledTwice;

      wrapper.setProps({ defaultValue: "" });
      instance.componentDidUpdate({
        ...commonProps,
        defaultValue: "different value",
      });
      expect(instance.resetValue).to.be.calledThrice;
    });
  });

  describe("#handleChange", () => {
    it.skip("should call props.onChange only if !reseting", () => {
      instance.reseting = true;
      instance.handleChange();
      expect(commonProps.onChange).to.not.be.called;
      instance.reseting = false;
      instance.handleChange();
      setTimeout(() => {
        expect(commonProps.onChange).to.have.been.called;
      }, 50);
    });

    it("should remove decorations if !reseting", () => {
      instance.reseting = false;
      stubMonacoEditorComponent();
      instance.handleChange();
      expect(
        instance.monacoEditorComponent.editor.deltaDecorations
      ).to.be.calledWith([], []);
    });
  });

  describe("#resetValue()", () => {
    it("should setValue", () => {
      stubMonacoEditorComponent();
      instance.resetValue();
      expect(
        instance.monacoEditorComponent.editor.executeEdits
      ).to.be.calledWith("dremio", [
        {
          identifier: "dremio-reset",
          range: undefined,
          text: commonProps.defaultValue,
        },
      ]);
      expect(instance.reseting).to.be.false;
    });

    it("should default defaultValue to empty string", () => {
      wrapper.setProps({ defaultValue: undefined });
      stubMonacoEditorComponent();
      instance.resetValue();
      expect(
        instance.monacoEditorComponent.editor.executeEdits
      ).to.be.calledWith("dremio", [
        {
          identifier: "dremio-reset",
          range: undefined,
          text: "",
        },
      ]);
    });
  });
});
