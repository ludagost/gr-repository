package it.tim.gr.service;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import it.tim.gr.ems.EMSMessageProducer;
import it.tim.gr.integration.proxy.AdjustBonusProxy;
import it.tim.gr.integration.proxy.CommitBonusProxy;
import it.tim.gr.integration.proxy.PaymentProxy;
import it.tim.gr.integration.proxy.PinProxy;
import it.tim.gr.integration.proxy.ReloadProxy;
import it.tim.gr.model.bonus.response.BonusResponse;
import it.tim.gr.model.input.ReloadRequest;
import it.tim.gr.model.payment.response.PaymentResponse;
import it.tim.gr.model.pinsystem.response.PinResponse;
import it.tim.gr.model.reload.response.ReloadResponse;
import it.tim.gr.rabbit.RabbitMessageProducer;
import lombok.extern.slf4j.Slf4j;

/**
 * Created by Luca D'Agostino on 9/09/18.
 */
@Service
@Slf4j
public class ReloadService {

	private static final DateTimeFormatter AUTH_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
	
	private PinProxy pinProxy;
	private ReloadProxy reloadProxy;
	private AdjustBonusProxy adjustBonusProxy;
	private CommitBonusProxy commitBonusProxy;    
    private PaymentProxy paymentProxy;
    
    private PinResponse pinResponse;
    private ReloadResponse reloadResponse;
    private BonusResponse bonusResponse;
    private PaymentResponse paymentResponse;
    
    private String xmlRequestMessage;
    private String jsonRequestMessage;    
    
    private XmlMapper xmlMapper;
    
    @Autowired
    private EMSMessageProducer emsMessageProducer;
    
    @Autowired
    private RabbitMessageProducer rabbitMessageProducer;
    
    @Autowired
	 ObjectMapper jsonMapper;           
   
    @Autowired
	public ReloadService(PinProxy pinProxy, ReloadProxy reloadProxy, AdjustBonusProxy adjustBonusProxy, CommitBonusProxy commitBonusProxy, PaymentProxy paymentProxy) {        
        this.pinProxy = pinProxy;
        this.reloadProxy = reloadProxy;
        this.adjustBonusProxy = adjustBonusProxy;
        this.commitBonusProxy = commitBonusProxy;
        this.paymentProxy = paymentProxy;
    }

    public String reload(ReloadRequest request){    	        
    	log.debug("Inizio");
		log.debug("Numero Linea" + request.getMessage().getNumLinea() + " - importo = " + request.getMessage().getImportoRicarica());								 
					        
		String response = null;
		
		xmlMapper = new XmlMapper();

		if (request.getMessage().getCodicePIN()!=null && !request.getMessage().getCodicePIN().equals("") &&
				request.getMessage().getIdPromo()!=null && !request.getMessage().getIdPromo().equals("") &&
				request.getMessage().getImportoBonus()!=null && !request.getMessage().getImportoBonus().equals("")) {							
				/*
				 * Traccia su Traking&Logging sostituito da LOG per KIBANA
				 */  							
			     log.debug("Pin valorizzato [" + request.getMessage().getCodicePIN()+"]");
			     
			     request.getMessage().setStatoPIN("ASSEGNATO");
				
				
			     pinResponse = pinProxy.pinReservation(request.getMessage().getNumLinea(),
			    													request.getMessage().getCodicePIN(),
			    													request.getMessage().getSubSys(),
			    													request.getHeaderHttp());
				 log.info("Richiesta PIN inviata. Linea ["+request.getMessage().getNumLinea()+"] PIN: ["+request.getMessage().getCodicePIN()+"] PIN: ["+request.getMessage().getSubSys()+"]");				 
				 
			     if (pinResponse==null || pinResponse.getEsito().equalsIgnoreCase("KO")) {
					/*
					 * Traccia su Traking&Logging sostituito con LOG
					 */
					log.error("Richiesta PIN Fallita. Esito ["+pinResponse.getEsito()+"] Causale: ["+pinResponse.getCausale()+"] Linea ["+request.getMessage().getNumLinea()+"] PIN: ["+request.getMessage().getCodicePIN()+"] PIN: ["+request.getMessage().getSubSys()+"]");					
					 
					request.getMessage().setCodiceStato("-502");
					request.getMessage().setDescrizioneStato("KO Ricarica online con promo pin (utilizzo bonus)");
					request.getMessage().setMotivazioneKO(" KO check PIN (BMV)");
					request.getMessage().setCodiceErrore(pinResponse!=null ? pinResponse.getCausale() : "");
					request.getMessage().setDescrizioneErrore(pinResponse!=null ? pinResponse.getEsito() : "");
					request.getMessage().setUltimaAzione("invocazione InitiativeCodeMgmt.CheckPinCodeReservation");					

					Date javaUtilDate= new Date();
					SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
					String formattedDate = formatter.format(javaUtilDate);					
					request.getMessage().setLastUpdate(formattedDate);
					
					try {						  						 
						xmlRequestMessage = xmlMapper.writeValueAsString(request);
						log.info("XML Richiesta ricevuta: "+xmlRequestMessage);
					} catch (JsonProcessingException e) {
						log.error("Impossibile generare XML dal messaggio di input");
						e.printStackTrace();
					}										
					
					/*
					 * Accoda messaggio per O2C su coda EMS
					 */
					emsMessageProducer.send(xmlRequestMessage);
					log.error("Messaggio inserito sulla cosa O2C +"+xmlRequestMessage);
				}			     				
		} 

		log.info("Invio ricarica Numero Linea ["+request.getMessage().getNumLinea()+"] Importo ["+request.getMessage().getImportoRicarica()+"] BasketMode 0");
		reloadResponse = reloadProxy.reload(request.getMessage().getNumLinea(), request.getMessage().getImportoRicarica());
		if (reloadResponse!=null && reloadResponse.getStatus().equals("OK")) {
			/*
			 * Traccia su Traking&Logging sostituito con LOG
			 */
			log.debug("ricarica con esito OK  Numero Linea ["+request.getMessage().getNumLinea()+"] Importo ["+request.getMessage().getImportoRicarica()+"]");
			
			if ((pinResponse!=null && pinResponse.getEsito().equalsIgnoreCase("OK")) || 
					(request.getMessage().getImportoBonus()!=null && !request.getMessage().getImportoBonus().equals("") &&
					 request.getMessage().getIdPromo()!=null && !request.getMessage().getIdPromo().equals(""))
					) {
				/*
				 * Traccia su Traking&Logging sostituito con LOG
				 */
				log.debug("Gestione Bonus");	

				
				bonusResponse = adjustBonusProxy.adjustBonus(request.getMessage().getNumLinea(), request.getMessage().getImportoBonus());
				if (bonusResponse!=null && bonusResponse.getStatus().equalsIgnoreCase("OK")) {
					/*
					 * Traccia su Traking&Logging sostituito con LOG
					 */
					log.debug("Ricarica Bonus OK. Esito ["+bonusResponse.getEsito()+"] ["+bonusResponse.getCausale()+"] Numero Linea ["+request.getMessage().getNumLinea()+"] Bonus ["+request.getMessage().getImportoBonus()+"]");
					
					bonusResponse = commitBonusProxy.commitBonus(); 
					if (bonusResponse!=null && bonusResponse.getStatus().equalsIgnoreCase("OK")) {
						/*
						 * Traccia su Traking&Logging sostituito con LOG
						 */
						log.debug("Commit Bonus esito OK");								
					} else {
						/*
						 * Traccia su Traking&Logging sostituito con LOG
						 */
						log.error("Commit Bonus Fallita con esito ["+bonusResponse.getEsito()+"] ["+bonusResponse.getCausale()+"] Numero Linea ["+request.getMessage().getNumLinea()+"] Bonus ["+request.getMessage().getImportoBonus()+"]");
											
						/*
						 * Messaggio fallimento BONUS(2) per O2C: SML su EMS 
						 */
						request.getMessage().setCodiceStato((request.getMessage().getCodicePIN()==null && request.getMessage().getCodicePIN().equals("")) ? "-501" : "-502");
						request.getMessage().setDescrizioneStato((request.getMessage().getCodicePIN()==null && request.getMessage().getCodicePIN().equals("")) ? "KO Ricarica online con promo one shot" : "KO Ricarica online con promo pin (utilizzo bonus)");
						request.getMessage().setMotivazioneKO("KO erogazione bonus (OPSC)");
						request.getMessage().setCodiceErrore(bonusResponse!=null ? bonusResponse.getEsito() : "");
						request.getMessage().setDescrizioneErrore(bonusResponse!=null ? bonusResponse.getCausale() : "");
						request.getMessage().setUltimaAzione("invocazione PrepaidMobileCreditMgmt.commit");					
						request.getMessage().setLastUpdate(this.composeDateTime());
						
						try {						  						 				
							xmlRequestMessage = xmlMapper.writeValueAsString(request);
							log.debug("XML mesaggio accodato: "+xmlRequestMessage);
							
						} catch (Exception e) {
							log.error("Impossibile generare XML dal messaggio di input");
							e.printStackTrace();
						}			
						
						/*
						 * Accoda messaggio su coda EMS O2C
						 */
						emsMessageProducer.send(xmlRequestMessage);
						log.info("Accodato messaggio XML: "+xmlRequestMessage);
					}
				} else {
					/*
					 * Traccia su Traking&Logging sostituito con LOG
					 */
					log.error("Ricarica Bonus Fallita con esito ["+bonusResponse.getEsito()+"] ["+bonusResponse.getCausale()+"] Numero Linea ["+request.getMessage().getNumLinea()+"] Bonus ["+request.getMessage().getImportoBonus()+"]");
										
					request.getMessage().setCodiceStato((request.getMessage().getCodicePIN()==null && request.getMessage().getCodicePIN().equals("")) ? "-501" : "-502");
					request.getMessage().setDescrizioneStato((request.getMessage().getCodicePIN()==null && request.getMessage().getCodicePIN().equals("")) ? "KO Ricarica online con promo one shot" : "KO Ricarica online con promo pin (utilizzo bonus)");
					request.getMessage().setMotivazioneKO("KO erogazione bonus (OPSC)");
					request.getMessage().setCodiceErrore(bonusResponse!=null ? bonusResponse.getEsito() : "");
					request.getMessage().setDescrizioneErrore(bonusResponse!=null ? bonusResponse.getCausale() : "");
					request.getMessage().setUltimaAzione("invocazione PrepaidMobileCreditMgmt.adjust");					
					request.getMessage().setLastUpdate(this.composeDateTime());
					
					try {						  						 				
						xmlRequestMessage = xmlMapper.writeValueAsString(request);
						log.debug("XML mesaggio accodato: "+xmlRequestMessage);
						
					} catch (Exception e) {
						log.error("Impossibile generare XML dal messaggio di input");
						e.printStackTrace();
					}			
					
					/*
					 * Accoda messaggio su coda EMS O2C
					 */
					emsMessageProducer.send(xmlRequestMessage);
					log.info("Accodato messaggio XML: "+xmlRequestMessage);
				}
				
			}
						
			try {
				Double valoreRicarica = Double.parseDouble(request.getMessage().getImportoRicarica());
				Double valoreAut = Double.parseDouble(request.getMessage().getIdAutorizzazionePagamento());
				
				paymentResponse = paymentProxy.payment(valoreRicarica.intValue(),
														"TIMMOTORIC",
														valoreAut.intValue(),
														"NTS",
														request.getMessage().getDataAutorizzazionePagamento(),
														request.getHeaderHttp().getTransactionID());
			} catch (Exception genEx) {
				log.error("Addebito FALLITO ["+paymentResponse.getTxHead().getResultCode()+"] ["+paymentResponse.getTxHead().getErrDescription()+"] Numero Linea ["+request.getMessage().getNumLinea()+"] Importo ["+request.getMessage().getImportoRicarica()+"] ID Pagamento ["+request.getMessage().getIdAutorizzazionePagamento()+"]" );
				paymentResponse = null;
				genEx.printStackTrace();
			}													
			
			if (paymentResponse!=null && paymentResponse.getTxHead().getResultCode().equalsIgnoreCase("IGFS_000")) {
				/*
				 * Traccia su Traking&Logging sostituito con LOG
				 */
				log.info("Addebito Riuscito ["+paymentResponse.getTxHead().getResultCode()+"] Numero Linea ["+request.getMessage().getNumLinea()+"] Importo ["+request.getMessage().getImportoRicarica()+"]");
				
				request.getMessage().setStatoPIN("BRUCIATO");
				
				/*
				 * Messaggio OK Ricarica JSON notifica per ESB-E
				 */
				request.getMessage().setCodiceStato("2");
				request.getMessage().setDescrizioneStato("Ricarica eseguita");;
				request.getMessage().setUltimaAzione("invocazione NETS.capture");
				request.getMessage().setLastUpdate(this.composeDateTime());				
				try {						  						 				
					jsonRequestMessage = jsonMapper.writeValueAsString(request);
					log.debug("JSON mesaggio: "+jsonRequestMessage);
					
				} catch (Exception e) {
					log.error("Impossibile generare JSON dal messaggio di input");
					e.printStackTrace();
				}			
				
				/*
				 * Mabbrica messaggio OK Ricarica per O2C: XML
				 */
				request.getMessage().setCodiceStato("3");
				request.getMessage().setDescrizioneStato("Ricarica non a buon fine");
				request.getMessage().setMotivazioneKO("KO pagamento");
				request.getMessage().setCodiceErrore(paymentResponse!=null ? paymentResponse.getTxHead().getResultCode() : "");
				request.getMessage().setDescrizioneErrore(paymentResponse!=null ? paymentResponse.getTxHead().getErrDescription() : "");
				request.getMessage().setUltimaAzione("invocazione NETS.capture");					
				request.getMessage().setLastUpdate(this.composeDateTime());
				
				try {						  						 				
					xmlRequestMessage = xmlMapper.writeValueAsString(request);
					log.debug("XML mesaggio accodato: "+xmlRequestMessage);
					
				} catch (Exception e) {
					log.error("Impossibile generare XML dal messaggio di input");
					e.printStackTrace();
				}			
				
				response = "OK";
			} else {
				log.error("Addebito FALLITO ["+paymentResponse.getTxHead().getResultCode()+"] ["+paymentResponse.getTxHead().getErrDescription()+"] Numero Linea ["+request.getMessage().getNumLinea()+"] Importo ["+request.getMessage().getImportoRicarica()+"]");
				
				request.getMessage().setCodiceStato("3");
				request.getMessage().setDescrizioneStato("Ricarica non a buon fine");
				request.getMessage().setMotivazioneKO("KO pagamento");
				request.getMessage().setCodiceErrore(paymentResponse!=null ? paymentResponse.getTxHead().getResultCode() : "");
				request.getMessage().setDescrizioneErrore(paymentResponse!=null ? paymentResponse.getTxHead().getErrDescription() : "");
				request.getMessage().setUltimaAzione("invocazione NETS.capture");					
				request.getMessage().setLastUpdate(this.composeDateTime());
				
				try {						  						 				
					xmlRequestMessage = xmlMapper.writeValueAsString(request);
					log.debug("XML mesaggio accodato: "+xmlRequestMessage);
					
				} catch (Exception e) {
					log.error("Impossibile generare XML dal messaggio di input");
					e.printStackTrace();
				}			
				
				/*
				 * Accoda messaggio su coda EMS O2C
				 */
				emsMessageProducer.send(xmlRequestMessage);
				log.info("Accodato messaggio XML: "+xmlRequestMessage);												
				
				response = "KO";
			}						
		} else {
			/*
			 * Traccia su Traking&Logging sostituito con LOG
			 */
			log.error("Ricarica Fallita con esito ["+reloadResponse.getEsito()+"] ["+reloadResponse.getDescrizioneEsito()+"] Numero Linea ["+request.getMessage().getNumLinea()+"] Importo ["+request.getMessage().getImportoRicarica()+"]");																
			
			/*
			 * Fabbrica messaggio KO Ricarica JSON notifica per ESB-E coda Rabbit
			 */
			request.getMessage().setCodiceStato("3");
			request.getMessage().setDescrizioneStato("Ricarica non a buon fine");
			request.getMessage().setMotivazioneKO("KO erogazione ricarica (OPSC)");
			request.getMessage().setCodiceErrore(reloadResponse!=null ? reloadResponse.getEsito() : "");
			request.getMessage().setDescrizioneErrore(reloadResponse!=null ? reloadResponse.getDescrizioneEsito() : "");
			request.getMessage().setUltimaAzione("invocazione PrepaidMobileTopUpMgmt.reload");					
			request.getMessage().setLastUpdate(this.composeDateTime());
			
			try {						  						 				
				jsonRequestMessage = jsonMapper.writeValueAsString(request);
				log.debug("JSON Messagio: "+jsonRequestMessage);
				
			} catch (JsonProcessingException e) {
				log.error("Impossibile generare JSON dal messaggio di input");
				e.printStackTrace();
			}
						
			/*
			 * Fabbrica messaggio KO Ricarica per O2C XML per coda EMS
			 */
			request.getMessage().setCodiceStato("3");
			request.getMessage().setDescrizioneStato("Ricarica non a buon fine");
			request.getMessage().setMotivazioneKO("KO erogazione ricarica (OPSC)");
			request.getMessage().setCodiceErrore(reloadResponse!=null ? reloadResponse.getEsito() : "");
			request.getMessage().setDescrizioneErrore(reloadResponse!=null ? reloadResponse.getDescrizioneEsito() : "");
			request.getMessage().setUltimaAzione("invocazione PrepaidMobileTopUpMgmt.reload");					
			request.getMessage().setLastUpdate(this.composeDateTime());
			
			try {						  						 				
				xmlRequestMessage = xmlMapper.writeValueAsString(request);
				log.debug("XML Messaggio: "+xmlRequestMessage);
				
			} catch (Exception e) {
				log.error("Impossibile generare XML dal messaggio di input");
				e.printStackTrace();
			}			
			
			response = "KO";
		}
		
		
		/*
		 * Accoda messaggio DOT ESB-E su coda RABBIT
		 */
		 rabbitMessageProducer.produceMsg(jsonRequestMessage);
		 log.info("Accodato Messaggio JSON su coda RABBIT: "+jsonRequestMessage);
		
		/*
		 * Accoda messaggio O2C su coda EMS
		 */
		emsMessageProducer.send(xmlRequestMessage);
		log.info("Accodato Messaggio XML su coda EMS: "+xmlRequestMessage);
		
		/*
		 * TODO: fabbrica messaggio dispositivo
		 */
		
		/*
		 * TODO: Accoda messaggio dispositivo su GR_outputDisp
		 */		
			
        log.debug("Reload completata ["+response+"]");    
        
        return response;
    }
    
    public String composeDateTime(){
    	
        LocalDateTime bankAuthDate = LocalDateTime.now();
        String authDate = bankAuthDate.format(AUTH_DATE_FORMATTER);
        return authDate;
    }

}