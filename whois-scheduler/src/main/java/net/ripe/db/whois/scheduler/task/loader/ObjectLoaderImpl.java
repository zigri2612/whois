package net.ripe.db.whois.scheduler.task.loader;

import net.ripe.db.whois.common.dao.RpslObjectDao;
import net.ripe.db.whois.common.dao.RpslObjectInfo;
import net.ripe.db.whois.common.dao.RpslObjectUpdateDao;
import net.ripe.db.whois.common.rpsl.*;
import net.ripe.db.whois.update.autokey.NicHandleFactory;
import net.ripe.db.whois.update.autokey.dao.OrganisationIdRepository;
import net.ripe.db.whois.update.autokey.dao.X509Repository;
import net.ripe.db.whois.update.domain.OrganisationId;
import net.ripe.db.whois.update.domain.X509KeycertId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ObjectLoaderImpl implements ObjectLoader {
    private static final Pattern X509_PATTERN = Pattern.compile("(?i)^X509-([1-9][0-9]+)$");
    private static final Pattern ORGID_PATTERN = Pattern.compile("(?i)^ORG-([A-Z]{2,4})([0-9]+)-([A-Z0-9_-]+)$");

    private final RpslObjectDao rpslObjectDao;
    private final RpslObjectUpdateDao rpslObjectUpdateDao;
    private final AttributeSanitizer attributeSanitizer;

    final NicHandleFactory nicHandleFactory;
    final OrganisationIdRepository organisationIdRepository;
    final X509Repository x509Repository;

    @Autowired
    public ObjectLoaderImpl(RpslObjectDao rpslObjectDao,
                            RpslObjectUpdateDao rpslObjectUpdateDao,
                            AttributeSanitizer attributeSanitizer,
                            NicHandleFactory nicHandleFactory,
                            OrganisationIdRepository organisationIdRepository,
                            X509Repository x509Repository) {
        this.rpslObjectDao = rpslObjectDao;
        this.rpslObjectUpdateDao = rpslObjectUpdateDao;
        this.attributeSanitizer = attributeSanitizer;
        this.nicHandleFactory = nicHandleFactory;
        this.organisationIdRepository = organisationIdRepository;
        this.x509Repository = x509Repository;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED, propagation = Propagation.REQUIRES_NEW)
    public void processObject(String fullObject, Result result, int pass) {
        if (fullObject.trim().length() == 0) {
            return;
        }

        RpslObject rpslObject = null;
        try {
            rpslObject = RpslObject.parse(fullObject);
            rpslObject = attributeSanitizer.sanitize(rpslObject, new ObjectMessages());
        } catch (Exception e) {
            result.addFail(pass, String.format("Syntax error: %s\nin object: \n%s\n", e.getMessage(), fullObject));
            return;
        }

        try {
            if (pass == 1) {
                final List<RpslAttribute> attributes = RpslObjectFilter.keepKeyAttributesOnly(rpslObject);
                rpslObject = new RpslObject((Integer) null, attributes);
                rpslObjectUpdateDao.createObject(rpslObject);
            } else {
                final RpslObjectInfo existing = rpslObjectDao.findByKey(rpslObject.getType(), rpslObject.getKey().toString());
                rpslObjectUpdateDao.updateObject(existing.getObjectId(), rpslObject);
                claimIds(rpslObject);
            }

            result.addSuccess(pass);
        } catch (Exception e) {
            result.addFail(pass, String.format("Error in pass %d in '%s': %s\n", pass, rpslObject.getFormattedKey(), e.getMessage()));
        }
    }

    private void claimIds(RpslObject rpslObject) throws Exception {
        final String key = rpslObject.getKey().toString();

        switch (rpslObject.getType()) {
            case PERSON:
            case ROLE:
                nicHandleFactory.claim(key);
                break;

            case ORGANISATION:
                // organisations would normally take AUTO-* keys only and generate a name, so we have to implement a manual claiming here
                Matcher orgMatcher = ORGID_PATTERN.matcher(key);
                if (!orgMatcher.matches()) {
                    throw new IllegalArgumentException("Organisation key '" + key + "' is invalid");
                }

                final String space = orgMatcher.group(1);
                final int index = orgMatcher.group(2).length() > 0 ? Integer.parseInt(orgMatcher.group(2)) : 0;
                final String suffix = orgMatcher.group(3);

                OrganisationId organisationId = new OrganisationId(space, index, suffix);
                organisationIdRepository.claimSpecified(organisationId);
                break;

            case KEY_CERT:
                Matcher keycertMatcher = X509_PATTERN.matcher(key);
                if (!keycertMatcher.matches()) {
                    break;  // could be pgp
                }
                final X509KeycertId autoKey = new X509KeycertId(null, Integer.parseInt(keycertMatcher.group(1)), null);
                x509Repository.claimSpecified(autoKey);
                break;
        }
    }
}
