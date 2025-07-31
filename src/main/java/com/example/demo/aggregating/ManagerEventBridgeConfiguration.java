package com.example.demo.aggregating;

import com.example.demo.EventBridgeMessageHandler;
import com.example.demo.UuidColumnRangePartitioner;
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
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.json.ObjectToJsonTransformer;
import org.springframework.messaging.MessageChannel;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.eventbridge.EventBridgeClient;

import java.net.URI;
import java.net.URISyntaxException;

/*
Aggregates response

 */
@Configuration
@EnableBatchProcessing
@EnableBatchIntegration
//@Profile("batch")
@RequiredArgsConstructor
public class ManagerEventBridgeConfiguration {


    private  final UuidColumnRangePartitioner  uuidColumnRangePartitioner; // Our custom partitioner

//    @Autowired
//    private  EventBridgeClient eventBridgeClient; // Asynchronous SQS client

    private final JobRepository jobRepository; // Job repository for Spring Batch

    private final RemotePartitioningManagerStepBuilderFactory managerStepBuilderFactory;


   // private final PlatformTransactionManager transactionManager; // Transaction manager for Spring Batch- only needed for manual
    
    public static final String REQUEST_QUEUE = "slaveRequests";
    public static final String REPLY_QUEUE = "masterReplies";


    @Bean
    public EventBridgeClient buildEventBridgeClient() throws URISyntaxException {
        return EventBridgeClient.builder()
                .region(Region.of("us-east-1"))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .endpointOverride(new URI("http", null, "localhost", 4566, null, null, null))
                .build();
    }

    @Bean
    public ObjectToJsonTransformer objectToJsonTransformer() {
        return new ObjectToJsonTransformer();
    }

    @Bean
    public EventBridgeMessageHandler eventBridgeOutboundMessageHandler() throws URISyntaxException {
        EventBridgeMessageHandler handler = new EventBridgeMessageHandler(buildEventBridgeClient());
       ; // Set the destination SQS queue
        return handler;
    }

    // Outbound flow to send partition requests to workers
    // ======================================================
	@Bean
	public DirectChannel  requests() {
		return new DirectChannel();
	}

	@Bean
	public IntegrationFlow outboundFlow() throws URISyntaxException {
		return IntegrationFlow.from(requests())
			 .transform(objectToJsonTransformer())
             .log()
             .handle(eventBridgeOutboundMessageHandler())
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
               // .inputChannel(repliesChannel())
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


//    @Bean
//    public IntegrationFlow inboundFlow() {
//        SqsMessageDrivenChannelAdapter adapter = new SqsMessageDrivenChannelAdapter(eventBridgeClient, REPLY_QUEUE);
//        // You can configure properties like maxNumberOfMessages, visibilityTimeout, etc.
//        // adapter.setMaxNumberOfMessages(10);
//        // adapter.setVisibilityTimeout(30); // seconds
//
//        return IntegrationFlow.from(adapter)
//                .channel(repliesChannel())
//                .get();
//    }




}
