package com.example.errorhandling;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.RedeliveryPolicy;
import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.component.jms.JmsMessageType;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.springframework.jms.connection.JmsTransactionManager;

public class JMSTransactionErrorTest extends CamelTestSupport {
	
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        
        RedeliveryPolicy redeliveryPolicy = new RedeliveryPolicy();
        redeliveryPolicy.setMaximumRedeliveries(0);
        
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
        connectionFactory.setBrokerURL("vm://localhost?broker.persistent=false");
        connectionFactory.setRedeliveryPolicy(redeliveryPolicy);
        
        JmsTransactionManager transactionManager = new JmsTransactionManager();
        transactionManager.setConnectionFactory(connectionFactory);
        
        JmsConfiguration configuration = new JmsConfiguration();
        configuration.setConnectionFactory(connectionFactory);
        configuration.setTransactionManager(transactionManager);
        configuration.setTransacted(true);
        configuration.setExplicitQosEnabled(true);
        configuration.setJmsMessageType(JmsMessageType.Text);
        configuration.setCacheLevelName("CACHE_CONSUMER");

        ActiveMQComponent activeMQComponent = new ActiveMQComponent();
        activeMQComponent.setConfiguration(configuration);
	
        camelContext.addComponent("activemq", activeMQComponent);
	
        return camelContext;
    }
    
    
    @Test
    public void testNoErrors() throws Exception {
    	MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
    	resultEndpoint.expectedMessageCount(1);
    	
    	template.sendBody("activemq:MY-CONSUMING.QUEUE", "here");
    	
    	assertMockEndpointsSatisfied();

    }
    
    @Test
    public void testWithError() throws Exception {
    	MockEndpoint resultEndpoint = getMockEndpoint("mock:result");
    	resultEndpoint.expectedMessageCount(0);
    	
    	
    	context.getRouteDefinitions().get(0).adviceWith(context, new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                interceptSendToEndpoint("mock:result")
                        .throwException(new Exception("A test exception"));
            }
        });
    	
    	template.sendBody("activemq:MY-CONSUMING.QUEUE", "here");
    	
    	Exchange deadLetterQueue = consumer.receive("activemq:ActiveMQ.DLQ", 10000);
    	
    	assertNotNull(deadLetterQueue);
    	assertMockEndpointsSatisfied();
    	
    }
    

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                
                from("activemq:MY-CONSUMING.QUEUE").log("Received ${body}").to("mock:result");
                
			}

		};
	}

}
