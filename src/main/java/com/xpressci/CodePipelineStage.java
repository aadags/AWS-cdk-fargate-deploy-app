package com.xpressci;

import software.amazon.awscdk.Stage;
import software.constructs.Construct;
import software.amazon.awscdk.StageProps;

public class CodePipelineStage extends Stage {
    public CodePipelineStage(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public CodePipelineStage(final Construct scope, final String id, final StageProps props) {
        super(scope, id, props);

        new CodePipelineStack(this, "WebService");
    }
}
