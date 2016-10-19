package de.hmag.callactivity;

import static org.junit.Assert.assertEquals;

import org.activiti.camel.ActivitiProducer;
import org.activiti.engine.HistoryService;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.history.HistoricProcessInstance;
import org.activiti.engine.history.HistoricProcessInstanceQuery;
import org.activiti.engine.history.HistoricVariableInstance;
import org.activiti.engine.repository.DeploymentBuilder;
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
  
  @Produce(uri="activiti:Child:Receive_from_Camel")
  private ProducerTemplate receiveTaskTemplate;
  
  @Test
  public void test() throws Exception {
    
    DeploymentBuilder deployment = repoService.createDeployment();
    deployment.addClasspathResource("diagrams/Child.bpmn");
    deployment.addClasspathResource("diagrams/Parent.bpmn");
    deployment.deploy();
    
    camelContext.addRoutes(new RouteBuilder() {
      @Override
      public void configure() throws Exception {
        from("seda:fromChild")
          .log("Got Message From seda Queue")
          .to("log:logger?showAll=true")
          .to(fromChildMock);
      }
    });
    
    ProcessInstance procInst = runtimeService.startProcessInstanceByKey("Parent");
    
    System.out.println("Started process with id:"+procInst.getId());
    
    fromChildMock.expectedMessageCount(1);
    fromChildMock.assertIsSatisfied();
    
    ExecutionQuery parentExecQuery = runtimeService.createExecutionQuery().processDefinitionKey("Parent");
    ExecutionQuery childExecQuery = runtimeService.createExecutionQuery().processDefinitionKey("Child");
    
    assertEquals(2, parentExecQuery.list().size());
    assertEquals(1, childExecQuery.list().size());
    
    Object procIdPropertyValue = fromChildMock.getExchanges().get(0).getProperty(ActivitiProducer.PROCESS_ID_PROPERTY);
    receiveTaskTemplate.sendBodyAndProperty("Body", ActivitiProducer.PROCESS_ID_PROPERTY,procIdPropertyValue);
    
    Thread.sleep(1000);
    
    assertEquals(0, parentExecQuery.list().size());
    assertEquals(0, childExecQuery.list().size());
    
    HistoricProcessInstanceQuery parentInstHistoryQuery = historyService.createHistoricProcessInstanceQuery().processDefinitionKey("Parent");
    HistoricProcessInstanceQuery childInstHistoryQuery = historyService.createHistoricProcessInstanceQuery().processDefinitionKey("Child");
    
    System.out.println("Parent history instances");
    outputHistory(parentInstHistoryQuery);
    System.out.println("Child history instances");
    outputHistory(childInstHistoryQuery);
    
    HistoricProcessInstance parentHisInst = parentInstHistoryQuery.singleResult();
    HistoricProcessInstance childHisInst = childInstHistoryQuery.singleResult();
    
    HistoricVariableInstance parentVariable = historyService.createHistoricVariableInstanceQuery().processInstanceId(parentHisInst.getId()).singleResult();
    HistoricVariableInstance childVariable  = historyService.createHistoricVariableInstanceQuery().processInstanceId(childHisInst.getId()).singleResult();
    
    assertEquals("childCamelBody",parentVariable.getVariableName());
    assertEquals("camelBody",     childVariable.getVariableName());
    
    assertEquals(childVariable.getValue(), parentVariable.getValue());
    
  }

  private void outputHistory(HistoricProcessInstanceQuery histInstQuery) {
    for (HistoricProcessInstance hisProcInst : histInstQuery.list()) {
      System.out.println(hisProcInst.getId());
      System.out.println("\this variables of procInst");
      for(HistoricVariableInstance varInst : historyService.createHistoricVariableInstanceQuery().processInstanceId(hisProcInst.getId()).list()) {
        System.out.println("\t"+varInst.getVariableName()+"="+varInst.getValue());
      }
    };
  }

}
