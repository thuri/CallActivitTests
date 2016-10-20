package de.hmag.callactivity;

import static org.junit.Assert.*;

import java.util.List;
import java.util.UUID;

import org.activiti.camel.ActivitiProducer;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.repository.DeploymentBuilder;
import org.activiti.engine.runtime.Execution;
import org.activiti.engine.runtime.ExecutionQuery;
import org.activiti.engine.runtime.ProcessInstance;
import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:context.xml")
public class CallActivityTest {

  @Autowired
  @Qualifier("runtimeService")
  private RuntimeService runtimeService;
  
  @Autowired
  @Qualifier("repositoryService")
  private RepositoryService repoService;
  
  @Autowired
  @Qualifier("historyService")
  private HistoryService historyService;
  
  @Autowired
  @Qualifier("camelContext")
  private CamelContext camelContext;
  
  @EndpointInject(uri="mock:fromChild")
  private MockEndpoint fromChildMock;
  
  @EndpointInject(uri="mock:parentResponseMock")
  private MockEndpoint parentResponseMock;
  
  @Produce(uri="activiti:Child:Receive_from_Camel")
  private ProducerTemplate receiveFromCamelProducer;
  
  @Produce(uri="activiti:Child:Receive_From_Camel_Before_End")
  private ProducerTemplate receiveFromCamelBeforeEndProducer;

  
  @Test
  public void test() throws Exception {
    
    /*
     * Arrange
     */
    DeploymentBuilder deployment = repoService.createDeployment();
    deployment.addClasspathResource("diagrams/Child.bpmn");
    deployment.addClasspathResource("diagrams/Parent.bpmn");
    deployment.deploy();
    
    camelContext.addRoutes(new RouteBuilder() {
      @Override
      public void configure() throws Exception {
        
        from("activiti:Child:call_Camel")
          .to(fromChildMock);
        
        from("activiti:Parent:RespondMQ")
          .to(parentResponseMock);
      }
    });
    
    //Create a unique string for the body value. This is to ensure that this body is passed from the child
    //to the parent and from the parent to the response
    String bodyValue = UUID.randomUUID().toString();
    //setup mock expectations
    parentResponseMock.expectedBodiesReceived(bodyValue);
    fromChildMock.expectedMessageCount(1);
    
    /*
     * Act & Assert
     */
    
    //start the Parent process which will start a process instance of the child process
    ProcessInstance procInst = runtimeService.startProcessInstanceByKey("Parent");
    assertNotNull(procInst);
    
    //the child process should sent a message through camel to the fromChildMock.
    //assert that this message has been received
    fromChildMock.assertIsSatisfied();
    
    //both instances will now be persisted and wait for a message to the first receive task in the child
    
    //define queries to ask for the executions of both processes
    ExecutionQuery parentExecQuery = runtimeService.createExecutionQuery().processDefinitionKey("Parent");
    ExecutionQuery childExecQuery = runtimeService.createExecutionQuery().processDefinitionKey("Child");
    
    //because the executions are waiting for a message in order to proceed there must be executions for both processes
    assertEquals(2, parentExecQuery.list().size());
    assertEquals(1, childExecQuery.list().size());
    
    //get the process instance id of the child which must have been sent to the fromChildMock as Exchange Property
    Object childProcId = fromChildMock.getExchanges().get(0).getProperty(ActivitiProducer.PROCESS_ID_PROPERTY);
    //now sent the message to the child in order to proceed in the process
    receiveFromCamelProducer.sendBodyAndProperty(bodyValue, ActivitiProducer.PROCESS_ID_PROPERTY,childProcId);
    
    //wait some time for the process to proceed
    Thread.sleep(1000);
    
    /*
     * Here we check that the process instances are still running 
     * (because they are waiting for the last receive task in the child)
     * but the response has been sent by the parent (parentResponseMock has received a message)
     */
    
    //first check that there are still executions left in the database
    assertEquals(2, parentExecQuery.list().size());
    assertEquals(1, childExecQuery.list().size());
    //now check that the parentResponseMock has received a message (expectation is set above)
    parentResponseMock.assertIsSatisfied();
    
    //now sent a message to the receive task of the child to complete both processes
    receiveFromCamelBeforeEndProducer.sendBodyAndProperty("go to sleep", ActivitiProducer.PROCESS_ID_PROPERTY, childProcId);
    
    Thread.sleep(1000);
    
    //Check that there are no executions left in the database
    assertEquals(0, parentExecQuery.list().size());
    assertEquals(0, childExecQuery.list().size());
    
  }

//  private void outputExecutions(List<Execution> execList) {
//    
//    for(Execution exec : execList) {
//      System.out.println(exec.getId());
//      System.out.println("\tparent: "+exec.getParentId());
//      System.out.println("\tsuper:"+((ExecutionEntity)exec).getSuperExecutionId());
//      System.out.println("\tactivity:"+exec.getActivityId());
//      System.out.println("\tvariables:"+runtimeService.getVariables(exec.getId()));
//      
//    }
//  }

}
