package com.example.demo.aggregating;

import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.integration.config.annotation.EnableBatchIntegration;
import org.springframework.batch.integration.partition.RemotePartitioningManagerStepBuilderFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.integration.aws.inbound.SqsMessageDrivenChannelAdapter;
import org.springframework.integration.aws.outbound.SqsMessageHandler;
import org.springframework.integration.channel.DirectChannel;


import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.json.ObjectToJsonTransformer;
import org.springframework.messaging.MessageChannel;
import com.example.demo.UuidColumnRangePartitioner;
import org.springframework.transaction.PlatformTransactionManager;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;

/*
Aggregates response

 */
@Configuration
@EnableBatchProcessing
@EnableBatchIntegration
@Profile("manager")
@RequiredArgsConstructor
public class ManagerConfiguration {


    private  final UuidColumnRangePartitioner  uuidColumnRangePartitioner; // Our custom partitioner


    private final SqsAsyncClient sqsAsyncClient; // Asynchronous SQS client

    private final JobRepository jobRepository; // Job repository for Spring Batch

    private final RemotePartitioningManagerStepBuilderFactory managerStepBuilderFactory;


   // private final PlatformTransactionManager transactionManager; // Transaction manager for Spring Batch- only needed for manual
    
    public static final String REQUEST_QUEUE = "slaveRequests";
    public static final String REPLY_QUEUE = "masterReplies";



     
    @Bean
    public ObjectToJsonTransformer objectToJsonTransformer() {
        return new ObjectToJsonTransformer();
    }

    @Bean
    public SqsMessageHandler sqsOutboundMessageHandler() {
        SqsMessageHandler handler = new SqsMessageHandler(sqsAsyncClient);
        handler.setQueue(REQUEST_QUEUE); // Set the destination SQS queue
        return handler;
    }

    // Outbound flow to send partition requests to workers
    // ======================================================
	@Bean
	public DirectChannel requests() {
		return new DirectChannel();
	}

	@Bean
	public IntegrationFlow outboundFlow() {
		return IntegrationFlow.from(requests())
			 .transform(objectToJsonTransformer())
             .log()
             .handle(sqsOutboundMessageHandler())
             .get();
	}
    
   @Bean
	public Job remotePartitioningJob() {
		return new JobBuilder("remotePartitioningJob", jobRepository).start(managerStep()).build();
	}


    @Bean
    public Step managerStep() {
        return this.managerStepBuilderFactory.get("masterStep")
                .partitioner("slaveStep", uuidColumnRangePartitioner) // Use our custom partitioner
                .gridSize(4) // The number of physical threads/processes to use for parallel execution
                .outputChannel(requests())
                .inputChannel(repliesChannel())
                .build();
    }


    @Bean
    public MessageChannel requestsChannel() {
        return new DirectChannel();
    }


    // Inbound flow to receive replies from workers
    // ======================================================
    @Bean
    public MessageChannel repliesChannel() {
        return new DirectChannel();
    }


    @Bean
    public IntegrationFlow inboundFlow() {
        SqsMessageDrivenChannelAdapter adapter = new SqsMessageDrivenChannelAdapter(sqsAsyncClient, REPLY_QUEUE);
        // You can configure properties like maxNumberOfMessages, visibilityTimeout, etc.
        // adapter.setMaxNumberOfMessages(10);
        // adapter.setVisibilityTimeout(30); // seconds

        return IntegrationFlow.from(adapter)
                .channel(repliesChannel())
                .get();
    }




}
