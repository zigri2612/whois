package net.ripe.db.whois.update.handler.validator.organisation;

import com.google.common.collect.Lists;
import net.ripe.db.whois.common.dao.jdbc.JdbcRpslObjectDao;
import net.ripe.db.whois.common.rpsl.ObjectType;
import net.ripe.db.whois.common.rpsl.RpslObject;
import net.ripe.db.whois.update.domain.Action;
import net.ripe.db.whois.update.domain.PreparedUpdate;
import net.ripe.db.whois.update.domain.UpdateContext;
import net.ripe.db.whois.update.domain.UpdateMessages;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Collections;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyCollection;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AbuseValidatorTest {
    @Mock PreparedUpdate update;
    @Mock UpdateContext updateContext;
    @Mock JdbcRpslObjectDao objectDao;

    @InjectMocks AbuseValidator subject;

    @Test
    public void getActions() {
        assertThat(subject.getActions(), containsInAnyOrder(Action.CREATE, Action.MODIFY));
    }

    @Test
    public void getTypes() {
        assertThat(subject.getTypes(), containsInAnyOrder(ObjectType.ORGANISATION));
    }

    @Test
    public void hasNoAbuseC() {
        when(update.getUpdatedObject()).thenReturn(RpslObject.parse("organisation: ORG-1"));

        subject.validate(update, updateContext);

        verifyZeroInteractions(updateContext);
    }

    @Test
    public void referencesRoleWithoutAbuseMailbox() {
        final RpslObject organisation = RpslObject.parse("organisation: ORG-1\nabuse-c: AB-NIC");
        when(update.getUpdatedObject()).thenReturn(organisation);

        final RpslObject role = RpslObject.parse("role: Role Test\nnic-hdl: AB-NIC");
        when(objectDao.getByKeys(eq(ObjectType.ROLE), anyCollection())).thenReturn(Lists.newArrayList(role));

        subject.validate(update, updateContext);

        verify(updateContext).addMessage(update, UpdateMessages.abuseMailboxRequired(role.getKey()));
    }

    @Test
    public void referencesRoleWithAbuseMailbox() {
        when(update.getUpdatedObject()).thenReturn(RpslObject.parse("organisation: ORG-1\nabuse-c: AB-NIC"));
        when(objectDao.getByKeys(eq(ObjectType.ROLE), anyCollection())).thenReturn(Lists.newArrayList(RpslObject.parse("role: Role Test\nnic-hdl: AB-NIC\nabuse-mailbox: abuse@test.net")));

        subject.validate(update, updateContext);

        verifyZeroInteractions(updateContext);
    }

    @Test
    public void referencesPersonInsteadOfRole() {
        when(update.getUpdatedObject()).thenReturn(RpslObject.parse("organisation: ORG-1\nabuse-c: AB-NIC"));
        when(objectDao.getByKeys(eq(ObjectType.ROLE), anyCollection())).thenReturn(Collections.EMPTY_LIST);
        when(objectDao.getByKeys(eq(ObjectType.PERSON), anyCollection())).thenReturn(Lists.newArrayList(RpslObject.parse("person: Some Person\nnic-hdl: AB-NIC")));

        subject.validate(update, updateContext);

        verify(updateContext).addMessage(update, UpdateMessages.abuseCPersonReference());
        verify(updateContext, never()).addMessage(update, UpdateMessages.abuseMailboxRequired("nic-hdl: AB-NIC"));
    }

    @Test
    public void referenceNotFound() {
        when(update.getUpdatedObject()).thenReturn(RpslObject.parse("organisation: ORG-1\nabuse-c: AB-NIC"));
        when(objectDao.getByKeys(eq(ObjectType.ROLE), anyCollection())).thenReturn(Collections.EMPTY_LIST);
        when(objectDao.getByKeys(eq(ObjectType.PERSON), anyCollection())).thenReturn(Collections.EMPTY_LIST);

        subject.validate(update, updateContext);

        verifyZeroInteractions(updateContext);
    }
}
