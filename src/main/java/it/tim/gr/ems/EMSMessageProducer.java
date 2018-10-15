package it.tim.gr.ems;
					
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class EMSMessageProducer {	
	
  private boolean esito;
  
  @Value("${gr.ems.queue}") 
  private String DESTINATION;    
 
  @Autowired
  private JmsTemplate jmsTemplate;

  public boolean send(String message) {
    log.debug("invio a EMS ["+DESTINATION+"] ["+message+"]" );
    try {
		jmsTemplate.convertAndSend(DESTINATION, message);
		log.info("Effettuato Invio alla coda EMS ["+DESTINATION+"] ["+message+"]" );
		esito = true;
	} catch (Exception e) {
		log.error("Fallito invio alla coda EMS ["+DESTINATION+"] ["+message+"]",e);
		esito = false;
	}
    
    return esito;
  }
}
