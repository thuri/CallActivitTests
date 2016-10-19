package de.hmag.callactivity;

import org.apache.camel.builder.RouteBuilder;

public class ChildRoutes extends RouteBuilder {

  @Override
  public void configure() throws Exception {
    from("activiti:Child:call_Camel").to("seda:fromChild");
  }

}
