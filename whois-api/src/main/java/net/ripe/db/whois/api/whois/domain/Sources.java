package net.ripe.db.whois.api.whois.domain;


import com.google.common.collect.Lists;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import java.util.Collection;
import java.util.List;

@XmlRootElement(name = "sources")
@XmlAccessorType(XmlAccessType.FIELD)
public class Sources {

    @XmlElement(name = "source")
    protected List<Source> sources;

    public Sources(final List<Source> sources) {
        this.sources = sources;
    }

    public Sources(final Collection<String> sources) {
        this.sources = Lists.newArrayList();
        for (String source : sources) {
            this.sources.add(new Source(source));
        }
    }

    public Sources() {
        // required no-arg constructor
    }

    public List<Source> getSources() {
        return sources;
    }
}
