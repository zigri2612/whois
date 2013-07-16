package net.ripe.db.whois.api.whois.rdap;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import net.ripe.db.whois.api.whois.rdap.domain.Autnum;
import net.ripe.db.whois.api.whois.rdap.domain.Domain;
import net.ripe.db.whois.api.whois.rdap.domain.Entity;
import net.ripe.db.whois.api.whois.rdap.domain.Event;
import net.ripe.db.whois.api.whois.rdap.domain.Ip;
import net.ripe.db.whois.api.whois.rdap.domain.Link;
import net.ripe.db.whois.api.whois.rdap.domain.Nameserver;
import net.ripe.db.whois.api.whois.rdap.domain.RdapObject;
import net.ripe.db.whois.api.whois.rdap.domain.Remark;
import net.ripe.db.whois.api.whois.rdap.domain.vcard.VCard;
import net.ripe.db.whois.common.domain.CIString;
import net.ripe.db.whois.common.domain.IpInterval;
import net.ripe.db.whois.common.domain.Ipv4Resource;
import net.ripe.db.whois.common.domain.Ipv6Resource;
import net.ripe.db.whois.common.domain.attrs.AsBlockRange;
import net.ripe.db.whois.common.domain.attrs.DsRdata;
import net.ripe.db.whois.common.domain.attrs.NServer;
import net.ripe.db.whois.common.rpsl.AttributeType;
import net.ripe.db.whois.common.rpsl.ObjectType;
import net.ripe.db.whois.common.rpsl.RpslAttribute;
import net.ripe.db.whois.common.rpsl.RpslObject;
import org.joda.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.ripe.db.whois.common.rpsl.ObjectType.INET6NUM;

class RdapObjectMapper {
    private static final Logger LOGGER = LoggerFactory.getLogger(RdapObjectMapper.class);

    private static final List<String> RDAP_CONFORMANCE_LEVEL = Lists.newArrayList("rdap_level_0");

    protected static DatatypeFactory dtf;
    static {
        try {
            dtf = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException dtfe) {
            LOGGER.error("Could not instantiate DatatypeFactory");
        }
    }

    private static final Set<AttributeType> CONTACT_ATTRIBUTES = Sets.newHashSet(AttributeType.ADMIN_C, AttributeType.TECH_C);
    private static final Map<AttributeType, String> CONTACT_ATTRIBUTE_TO_ROLE_NAME = Maps.newHashMap();
    static {
        CONTACT_ATTRIBUTE_TO_ROLE_NAME.put(AttributeType.ADMIN_C, "administrative");
        CONTACT_ATTRIBUTE_TO_ROLE_NAME.put(AttributeType.TECH_C, "technical");
        CONTACT_ATTRIBUTE_TO_ROLE_NAME.put(AttributeType.MNT_BY, "registrant");
    }

    @Value("${rdap.public.port43:whois.ripe.net}")
    private static String port43;

    public static Object map(
            final String requestUrl,
            final String baseUrl,
            final RpslObject rpslObject,
            final List<RpslObject> relatedObjects,
            final LocalDateTime lastChangedTimestamp,
            final List<RpslObject> abuseContacts,
            final RpslObject parentRpslObject) {

        RdapObject rdapResponse;
        final ObjectType rpslObjectType = rpslObject.getType();

        switch (rpslObjectType) {
            case DOMAIN:
                rdapResponse = createDomain(rpslObject);
                break;
            case AUT_NUM:
            case AS_BLOCK:
                rdapResponse = createAutnumResponse(rpslObject);
                break;
            case INETNUM:
            case INET6NUM:
                rdapResponse = createIp(rpslObject, parentRpslObject, requestUrl, baseUrl);
                break;
            case PERSON:
            case ROLE:
            // TODO: [RL] Configuration (property?) for allowed RPSL object types for entity lookups
            // TODO: [ES] Denis to review
            case ORGANISATION:
            case IRT:
                rdapResponse = createEntity(rpslObject);
                break;
            default:
                throw new IllegalArgumentException("Unhandled object type: " + rpslObject.getType());
        }

        rdapResponse.getRdapConformance().addAll(RDAP_CONFORMANCE_LEVEL);
        rdapResponse.getLinks().add(createLink("self", requestUrl, requestUrl));
        rdapResponse.setPort43(port43);
        rdapResponse.getNotices().addAll(NoticeFactory.generateNotices(rpslObject, requestUrl));

        final List<Remark> remarks = createRemarks(rpslObject);
        if (!remarks.isEmpty()) {
            rdapResponse.getRemarks().addAll(remarks);
        }
        rdapResponse.getEvents().add(createEvent(lastChangedTimestamp));

        for (final RpslObject abuseContact : abuseContacts) {
            rdapResponse.getEntities().add(createEntity(abuseContact));
        }
        rdapResponse.getEntities().addAll(contactEntities(rpslObject, relatedObjects, requestUrl, baseUrl));

        return rdapResponse;
    }

    private static Ip createIp(final RpslObject rpslObject, final RpslObject parentRpslObject, final String requestUrl, final String baseUrl) {
        final Ip ip = new Ip();
        ip.setHandle(rpslObject.getKey().toString());
        IpInterval ipInterval;
        if (rpslObject.getType() == INET6NUM) {
            ipInterval = Ipv6Resource.parse(rpslObject.getKey());
            ip.setIpVersion("v6");
        } else {
            ipInterval = Ipv4Resource.parse(rpslObject.getKey());
            ip.setIpVersion("v4");
        }

        // TODO: find a better way to remove the cidr notation
        String startAddr = IpInterval.asIpInterval(ipInterval.beginAsInetAddress()).toString();
        String endAddr = IpInterval.asIpInterval(ipInterval.endAsInetAddress()).toString();
        ip.setStartAddress(startAddr.split("/")[0]);
        ip.setEndAddress(endAddr.split("/")[0]);

        ip.setName(rpslObject.getValueForAttribute(AttributeType.NETNAME).toString());
        ip.setCountry(rpslObject.getValueForAttribute(AttributeType.COUNTRY).toString());
        ip.setLang(rpslObject.getValuesForAttribute(AttributeType.LANGUAGE).isEmpty() ? null : Joiner.on(",").join(rpslObject.getValuesForAttribute(AttributeType.LANGUAGE)));
        ip.setType(rpslObject.getValueForAttribute(AttributeType.STATUS).toString());

        if (parentRpslObject != null) {
            ip.setParentHandle(parentRpslObject.getValueForAttribute(AttributeType.NETNAME).toString());

            CIString parentKey = parentRpslObject.getKey();
            IpInterval parentInterval = (parentRpslObject.getType() == INET6NUM) ? Ipv6Resource.parse(parentKey) : Ipv4Resource.parse(parentKey);
            String prefix = IpInterval.asIpInterval(parentInterval.beginAsInetAddress()).toString().split("/")[0] + "/" + parentInterval.getPrefixLength();
            ip.getLinks().add(createLink("up", requestUrl,
                                         baseUrl + "/ip/" + prefix));
        }

        return ip;
    }

    private static List<Remark> createRemarks(final RpslObject rpslObject) {
        final List<Remark> remarkList = Lists.newArrayList();

        final List<String> descriptions = Lists.newArrayList();

        for (final CIString description : rpslObject.getValuesForAttribute(AttributeType.DESCR)) {
            descriptions.add(description.toString());
        }

        if (!descriptions.isEmpty()) {
            remarkList.add(createRemark(descriptions));
        }

        final List<String> remarks = Lists.newArrayList();

        for (final CIString description : rpslObject.getValuesForAttribute(AttributeType.REMARKS)) {
            remarks.add(description.toString());
        }

        if (!remarks.isEmpty()) {
            remarkList.add(createRemark(remarks));
        }

        return remarkList;
    }

    private static Event createEvent(final LocalDateTime lastChanged) {
        final Event lastChangedEvent = new Event();
        lastChangedEvent.setEventAction("last changed");
        lastChangedEvent.setEventDate(convertToXMLGregorianCalendar(lastChanged).toString());
        return lastChangedEvent;
    }

    private static List<Entity> contactEntities(final RpslObject rpslObject, List<RpslObject> relatedObjects, final String requestUrl, final String baseUrl) {
        final List<Entity> entities = Lists.newArrayList();

        final Map<String, Set<AttributeType>> contacts = Maps.newHashMap();

        final Map<String, RpslObject> relatedObjectMap = Maps.newHashMap();
        for (final RpslObject object : relatedObjects) {
            relatedObjectMap.put(object.getKey().toString(), object);
        }

        for (final RpslAttribute attribute : rpslObject.findAttributes(CONTACT_ATTRIBUTES)) {
            final String contactName = attribute.getCleanValue().toString();
            if (contacts.containsKey(contactName)) {
                contacts.get(contactName).add(attribute.getType());
            } else {
                final Set<AttributeType> attributeTypes = Sets.newHashSet();
                attributeTypes.add(attribute.getType());
                contacts.put(contactName, attributeTypes);
            }
        }

        for (Map.Entry<String, Set<AttributeType>> entry : contacts.entrySet()) {
            Entity entity;
            final RpslObject object = relatedObjectMap.get(entry.getKey());
            if (object != null) {
                entity = createNestedEntity(object, Lists.<RpslObject>newArrayList(), requestUrl, baseUrl);
            } else {
                entity = new Entity();
                entity.setHandle(entry.getKey());
            }
            for (AttributeType attributeType : entry.getValue()) {
                entity.getRoles().add(CONTACT_ATTRIBUTE_TO_ROLE_NAME.get(attributeType));
            }
            entities.add(entity);
        }

        return entities;
    }

    private static Entity createEntity(final RpslObject rpslObject) {
        final Entity entity = new Entity();
        entity.setHandle(rpslObject.getKey().toString());
        setVCardArray(entity,createVCard(rpslObject));

        return entity;
    }

    private static Entity createNestedEntity(final RpslObject rpslObject, final List<RpslObject> relatedObjects, final String requestUrl, final String baseUrl) {
        final Entity entity = createEntity(rpslObject);

        final List<Remark> remarks = createRemarks(rpslObject);
        if (!remarks.isEmpty()) {
            entity.getRemarks().addAll(remarks);
        }

        final String selfUrl = baseUrl + "/entity/" + entity.getHandle();
        entity.getLinks().add( createLink("self", requestUrl, selfUrl));

        List<Entity> contactEntities = contactEntities(rpslObject, relatedObjects, requestUrl, baseUrl);
        if (!contactEntities.isEmpty()) {
            entity.getEntities().addAll(contactEntities);
        }
        // TODO: [RL] Add abuse contact here?

        return entity;
    }

    private static Autnum createAutnumResponse(final RpslObject rpslObject) {
        final Autnum autnum = new Autnum();
        final String keyValue = rpslObject.getKey().toString();
        autnum.setHandle(keyValue);

        if (rpslObject.getType() == ObjectType.AS_BLOCK) {
            AsBlockRange asBlockRange = AsBlockRange.parse(keyValue);
            autnum.setStartAutnum(asBlockRange.getBegin());
            autnum.setEndAutnum(asBlockRange.getEnd());
            autnum.setName(keyValue);
        } else {
            final CIString autnumAttributeValue = rpslObject.getValueForAttribute(AttributeType.AUT_NUM);
            final long startAndEnd = Long.valueOf(autnumAttributeValue.toString().replace("AS", "").replace(" ", ""));
            autnum.setStartAutnum(startAndEnd);
            autnum.setEndAutnum(startAndEnd);
            autnum.setName(rpslObject.getValueForAttribute(AttributeType.AS_NAME).toString().replace(" ", ""));
            autnum.setType("DIRECT ALLOCATION");
        }

        return autnum;
    }

    private static Domain createDomain(final RpslObject rpslObject) {
        final Domain domain = new Domain();
        domain.setHandle(rpslObject.getKey().toString());
        domain.setLdhName(rpslObject.getKey().toString());

        final HashMap<CIString, Set<IpInterval>> hostnameMap = new HashMap<>();

        for (final RpslAttribute rpslAttribute : rpslObject.findAttributes(AttributeType.NSERVER)) {
            final NServer nserver = NServer.parse(rpslAttribute.getCleanValue().toString());

            final CIString hostname = nserver.getHostname();

            final Set<IpInterval> ipIntervalSet;
            if (hostnameMap.containsKey(hostname)) {
                ipIntervalSet = hostnameMap.get(hostname);
            } else {
                ipIntervalSet = Sets.newHashSet();
                hostnameMap.put(hostname, ipIntervalSet);
            }

            final IpInterval ipInterval = nserver.getIpInterval();
            if (ipInterval != null) {
                ipIntervalSet.add(ipInterval);
            }
        }

        for (final CIString hostname : hostnameMap.keySet()) {
            final Nameserver nameserver = new Nameserver();
            nameserver.setLdhName(hostname.toString());

            final Set<IpInterval> ipIntervals = hostnameMap.get(hostname);
            if (!ipIntervals.isEmpty()) {

                final Nameserver.IpAddresses ipAddresses = new Nameserver.IpAddresses();
                for (IpInterval ipInterval : ipIntervals) {
                    if (ipInterval instanceof Ipv4Resource) {
                        ipAddresses.getIpv4().add(IpInterval.asIpInterval(ipInterval.beginAsInetAddress()).toString());
                    } else if (ipInterval instanceof Ipv6Resource) {
                        ipAddresses.getIpv6().add(IpInterval.asIpInterval(ipInterval.beginAsInetAddress()).toString());
                    }
                }
                nameserver.setIpAddresses(ipAddresses);
            }

            domain.getNameServers().add(nameserver);
        }

        final Domain.SecureDNS secureDNS = new Domain.SecureDNS();
        secureDNS.setDelegationSigned(false);

        for (final CIString rdata : rpslObject.getValuesForAttribute(AttributeType.DS_RDATA)) {
            final DsRdata dsRdata = DsRdata.parse(rdata);

            secureDNS.setDelegationSigned(true);

            final Domain.SecureDNS.DsData dsData = new Domain.SecureDNS.DsData();
            dsData.setKeyTag(dsRdata.getKeytag());
            dsData.setAlgorithm((short)dsRdata.getAlgorithm());
            dsData.setDigestType(dsRdata.getDigestType());
            dsData.setDigest(dsRdata.getDigestHexString());

            secureDNS.getDsData().add(dsData);
        }

        if (secureDNS.isDelegationSigned()) {
            domain.setSecureDNS(secureDNS);
        }

        return domain;
    }

    private static VCard createVCard(final RpslObject rpslObject) {
        final VCardBuilder builder = new VCardBuilder();
        builder.addVersion();

        switch (rpslObject.getType()) {
            case PERSON:
                for (final RpslAttribute attribute : rpslObject.findAttributes(AttributeType.PERSON)) {
                    builder.addFn(attribute.getCleanValue().toString());
                }
                builder.addKind("individual");
                break;
            case ORGANISATION:
                for (final RpslAttribute attribute : rpslObject.findAttributes(AttributeType.ORG_NAME)) {
                    builder.addFn(attribute.getCleanValue().toString());
                }
                builder.addKind("org");
                break;
            case ROLE:
                for (final RpslAttribute attribute : rpslObject.findAttributes(AttributeType.ROLE)) {
                    builder.addFn(attribute.getCleanValue().toString());
                }
                builder.addKind("group");
                break;
            case IRT:
                for (final RpslAttribute attribute : rpslObject.findAttributes(AttributeType.IRT)) {
                    builder.addFn(attribute.getCleanValue().toString());
                }
                builder.addKind("group");
                break;
            default:
                break;
        }

        List<CIString> addrList = new ArrayList<CIString>();
        for (final CIString address : rpslObject.getValuesForAttribute(AttributeType.ADDRESS)) {
            addrList.add(address);
        }
        if (!addrList.isEmpty()) {
            String addr = Joiner.on("\n").join(addrList.listIterator());
            builder.addAdr(VCardHelper.createMap(Maps.immutableEntry("type", "work"), Maps.immutableEntry("label", addr)), null);
        }

        for (final CIString phone : rpslObject.getValuesForAttribute(AttributeType.PHONE)) {
            builder.addTel(VCardHelper.createMap(Maps.immutableEntry("type", Lists.newArrayList("work", "voice"))),phone.toString());
        }

        for (final CIString fax : rpslObject.getValuesForAttribute(AttributeType.FAX_NO)) {
            builder.addTel(VCardHelper.createMap(Maps.immutableEntry("type", "work")),fax.toString());
        }

        for (final CIString email : rpslObject.getValuesForAttribute(AttributeType.E_MAIL)) {
            // TODO ?? Is it valid to have more than 1 email
            builder.addEmail(VCardHelper.createMap(Maps.immutableEntry("type", "work")),email.toString());
        }

        for (final CIString org : rpslObject.getValuesForAttribute(AttributeType.ORG)) {
            builder.addOrg(org.toString());
        }

        for (final CIString geoloc : rpslObject.getValuesForAttribute(AttributeType.GEOLOC)) {
            builder.addGeo(VCardHelper.createMap(Maps.immutableEntry("type", "work")), geoloc.toString());
        }

        return builder.build();
    }

    public static XMLGregorianCalendar convertToXMLGregorianCalendar(final LocalDateTime lastChanged) {
        GregorianCalendar gc =  new GregorianCalendar();
        gc.setTimeInMillis(lastChanged.toDateTime().getMillis());
        return dtf.newXMLGregorianCalendar(gc);
    }

    public static Link createLink(String rel, String value, String href) {
        Link link = new Link();
        link.setRel(rel);
        link.setValue(value);
        link.setHref(href);
        return link;
    }

    public static Remark createRemark(List<String> remarks) {
        Remark remark = new Remark();
        remark.getDescription().addAll(remarks);
        return remark;
    }

    public static void setVCardArray(Entity entity, final VCard... vCards) {
        List<Object> vcardArray = entity.getVcardArray();
        vcardArray.add("vcard");
        for (VCard next : vCards) {
            vcardArray.add(next.getValues());
        }
    }
}
