package com.xpressci;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.pipelines.CodePipeline;
import software.amazon.awscdk.pipelines.CodePipelineSource;
import software.amazon.awscdk.pipelines.ShellStep;
import software.amazon.awscdk.services.codecommit.Repository;
import software.amazon.awscdk.services.ecr.IRepository;
import software.amazon.awscdk.services.ecr.RepositoryAttributes;
import software.constructs.Construct;

import java.util.Arrays;

public class CodePipelineStack extends Stack {

    public CodePipelineStack(final Construct parent, final String id) {
        this(parent, id, null);
    }

    public CodePipelineStack(final Construct parent, final String id, final StackProps props) {
        super(parent, id, props);

//        final Repository repo = Repository.Builder.create(this, "WorkshopRepo")
//                .repositoryName("WorkshopRepo")
//                .build();


        final CodePipeline pipeline = CodePipeline.Builder.create(this, "Pipeline")
                .pipelineName("CDKPipeline")
                .synth(ShellStep.Builder.create("SynthStep")
                        .input(CodePipelineSource.gitHub("", ""))
                        .installCommands(Arrays.asList("npm install -g aws-cdk", "mvn package", "npx cdk synth"))
                        .build())
                .build();

        final CodePipelineStage deploy = new CodePipelineStage(this, "Deploy");
        pipeline.addStage(deploy);
    }
}
