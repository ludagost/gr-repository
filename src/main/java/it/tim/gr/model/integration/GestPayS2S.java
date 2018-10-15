package it.tim.gr.model.integration;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;

import lombok.Data;


@XmlAccessorType(XmlAccessType.FIELD)
@Data
public class GestPayS2S {

    @XmlElement(name = "TransactionType")
    private String transactionType;

    @XmlElement(name = "TransactionResult")
    private String transactionResult;

    @XmlElement(name = "ShopTransactionID")
    private String shopTransactionId;

    @XmlElement(name = "BankTransactionID")
    private String bankTransactionId;

    @XmlElement(name = "AuthorizationCode")
    private String authCode;

    @XmlElement(name = "Currency")
    private String currency;

    @XmlElement(name = "Country")
    private String country;


}
