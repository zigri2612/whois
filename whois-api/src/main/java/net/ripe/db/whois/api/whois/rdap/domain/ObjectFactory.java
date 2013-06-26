//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vhudson-jaxb-ri-2.1-2 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2013.06.26 at 08:21:13 PM EST 
//


package net.ripe.db.whois.api.whois.rdap.domain;

import javax.xml.bind.annotation.XmlRegistry;


/**
 * This object contains factory methods for each 
 * Java content interface and Java element interface 
 * generated in the net.ripe.db.whois.api.whois.rdap.domain package. 
 * <p>An ObjectFactory allows you to programatically 
 * construct new instances of the Java representation 
 * for XML content. The Java representation of XML 
 * content can consist of schema derived interfaces 
 * and classes representing the binding of schema 
 * type definitions, element declarations and model 
 * groups.  Factory methods for each of these are 
 * provided in this class.
 * 
 */
@XmlRegistry
public class ObjectFactory {


    /**
     * Create a new ObjectFactory that can be used to create new instances of schema derived classes for package: net.ripe.db.whois.api.whois.rdap.domain
     * 
     */
    public ObjectFactory() {
    }

    /**
     * Create an instance of {@link Domain }
     * 
     */
    public Domain createDomain() {
        return new Domain();
    }

    /**
     * Create an instance of {@link Nameserver.IpAddresses }
     * 
     */
    public Nameserver.IpAddresses createNameserverIpAddresses() {
        return new Nameserver.IpAddresses();
    }

    /**
     * Create an instance of {@link Nameserver.Links }
     * 
     */
    public Nameserver.Links createNameserverLinks() {
        return new Nameserver.Links();
    }

    /**
     * Create an instance of {@link Nameserver }
     * 
     */
    public Nameserver createNameserver() {
        return new Nameserver();
    }

    /**
     * Create an instance of {@link Entity }
     * 
     */
    public Entity createEntity() {
        return new Entity();
    }

    /**
     * Create an instance of {@link Ip }
     * 
     */
    public Ip createIp() {
        return new Ip();
    }

    /**
     * Create an instance of {@link Ip.Remarks }
     * 
     */
    public Ip.Remarks createIpRemarks() {
        return new Ip.Remarks();
    }

    /**
     * Create an instance of {@link Nameserver.Remarks }
     * 
     */
    public Nameserver.Remarks createNameserverRemarks() {
        return new Nameserver.Remarks();
    }

    /**
     * Create an instance of {@link Ip.Links }
     * 
     */
    public Ip.Links createIpLinks() {
        return new Ip.Links();
    }

    /**
     * Create an instance of {@link Nameserver.Events }
     * 
     */
    public Ip.Links createIpLinks() {
        return new Ip.Links();
    }

    /**
     * Create an instance of {@link Ip.Events }
     * 
     */
    public Ip.Events createIpEvents() {
        return new Ip.Events();
    }

}
