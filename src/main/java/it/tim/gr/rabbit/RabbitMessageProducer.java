package it.tim.gr.rabbit;

import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.rabbitmq.tools.json.JSONWriter;

import it.tim.gr.model.input.ReloadMessageBody;
import it.tim.gr.model.input.ReloadMessageHeader;
import it.tim.gr.model.input.ReloadRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RabbitMessageProducer {
	
	@Value("${gr.rabbit.queue.exchange}") 
	private String EXCHANGE;
	
	@Value("${gr.rabbit.queue.routing_key}") 
	private String ROUTING_KEY;
					
	@Autowired
	private AmqpTemplate amqpTemplate;
	
	public void produceMsg(String message){
		amqpTemplate.convertAndSend(EXCHANGE, ROUTING_KEY, message);
		log.debug("Messaggio inviato: [" + message + "]");
	}	
}