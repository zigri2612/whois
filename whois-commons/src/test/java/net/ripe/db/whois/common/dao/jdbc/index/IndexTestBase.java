package net.ripe.db.whois.common.dao.jdbc.index;

import net.ripe.db.whois.common.rpsl.RpslObject;
import net.ripe.db.whois.common.support.AbstractDaoTest;

public abstract class IndexTestBase extends AbstractDaoTest {
    public int getObjectId(final RpslObject rpslObject) {
        return whoisTemplate.queryForInt("SELECT object_id FROM last WHERE pkey = ?", rpslObject.getKey().toString());
    }
}
