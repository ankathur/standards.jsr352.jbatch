//
// This file was generated by the JavaTM Architecture for XML Binding(JAXB) Reference Implementation, vIBM 2.2.3-11/28/2011 06:21 AM(foreman)- 
// See <a href="http://java.sun.com/xml/jaxb">http://java.sun.com/xml/jaxb</a> 
// Any modifications to this file will be lost upon recompilation of the source schema. 
// Generated on: 2013.03.12 at 05:49:49 PM EDT 
//


package com.ibm.jbatch.jsl.model;

import javax.annotation.Generated;
import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlType;


/**
 * <p>Java class for Next complex type.
 * 
 * <p>The following schema fragment specifies the expected content contained within this class.
 * 
 * <pre>
 * &lt;complexType name="Next">
 *   &lt;complexContent>
 *     &lt;restriction base="{http://www.w3.org/2001/XMLSchema}anyType">
 *       &lt;attribute name="on" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *       &lt;attribute name="to" use="required" type="{http://www.w3.org/2001/XMLSchema}string" />
 *     &lt;/restriction>
 *   &lt;/complexContent>
 * &lt;/complexType>
 * </pre>
 * 
 * 
 */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "Next")
@Generated(value = "com.ibm.jtc.jax.tools.xjc.Driver", date = "2013-03-12T05:49:49-04:00", comments = "JAXB RI v2.2.3-11/28/2011 06:21 AM(foreman)-")
/**
 * Modified post-XJC-gen by custom JSR352 RI build logic 
 * since we can't seem to get JAXB's XJC to generate 
 * elements implementing a common interface. 
 *
 * This custom logic adds the interface implementation : 
 *      implements com.ibm.jbatch.container.jsl.TransitionElement 
 */
 public class Next implements com.ibm.jbatch.container.jsl.TransitionElement {

    @XmlAttribute(name = "on", required = true)
    @Generated(value = "com.ibm.jtc.jax.tools.xjc.Driver", date = "2013-03-12T05:49:49-04:00", comments = "JAXB RI v2.2.3-11/28/2011 06:21 AM(foreman)-")
    protected String on;
    @XmlAttribute(name = "to", required = true)
    @Generated(value = "com.ibm.jtc.jax.tools.xjc.Driver", date = "2013-03-12T05:49:49-04:00", comments = "JAXB RI v2.2.3-11/28/2011 06:21 AM(foreman)-")
    protected String to;

    /**
     * Gets the value of the on property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.ibm.jtc.jax.tools.xjc.Driver", date = "2013-03-12T05:49:49-04:00", comments = "JAXB RI v2.2.3-11/28/2011 06:21 AM(foreman)-")
    public String getOn() {
        return on;
    }

    /**
     * Sets the value of the on property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.ibm.jtc.jax.tools.xjc.Driver", date = "2013-03-12T05:49:49-04:00", comments = "JAXB RI v2.2.3-11/28/2011 06:21 AM(foreman)-")
    public void setOn(String value) {
        this.on = value;
    }

    /**
     * Gets the value of the to property.
     * 
     * @return
     *     possible object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.ibm.jtc.jax.tools.xjc.Driver", date = "2013-03-12T05:49:49-04:00", comments = "JAXB RI v2.2.3-11/28/2011 06:21 AM(foreman)-")
    public String getTo() {
        return to;
    }

    /**
     * Sets the value of the to property.
     * 
     * @param value
     *     allowed object is
     *     {@link String }
     *     
     */
    @Generated(value = "com.ibm.jtc.jax.tools.xjc.Driver", date = "2013-03-12T05:49:49-04:00", comments = "JAXB RI v2.2.3-11/28/2011 06:21 AM(foreman)-")
    public void setTo(String value) {
        this.to = value;
    }

}
