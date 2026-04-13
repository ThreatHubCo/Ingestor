package co.threathub.ingestor.js.api;

import co.threathub.ingestor.Ingestor;
import co.threathub.ingestor.halo.model.HaloTicket;
import co.threathub.ingestor.js.JSValueConverter;
import co.threathub.ingestor.js.exception.ScriptException;
import co.threathub.ingestor.model.Customer;
import co.threathub.ingestor.model.Software;
import co.threathub.ingestor.util.Utils;
import lombok.RequiredArgsConstructor;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.HashMap;
import java.util.Map;

@RequiredArgsConstructor
public class TicketApi {
    private final Ingestor ingestor;

    @HostAccess.Export
    public void create(Value customerId, Value softwareId) {
        create(customerId, softwareId, null);
    }

    @HostAccess.Export
    public void create(Value customerId, Value softwareId, Value options) {
        try {
            Customer customer = ingestor.getCustomerRepository().getCustomerById(customerId.asInt());
            Software software = ingestor.getSoftwareRepository().getSoftwareById(softwareId.asInt());

            Map<String, Object> model = new HashMap<>();
            model.put("customer", customer);
            model.put("software", software);

            if (options != null && options.hasMembers()) {
                for (String key : options.getMemberKeys()) {
                    Value v = options.getMember(key);
                    model.put(key, JSValueConverter.convertValue(v));
                }
            }

            ingestor.getHaloClient().createTicket(customer, software, "SOFTWARE_ESCALATION", model);
        } catch (Exception ex) {
            throw new ScriptException("Failed to create ticket: " + ex.getMessage(), ex);
        }
    }

    @HostAccess.Export
    public Value lookup(String ticketId) {
        try {
            HaloTicket ticket = ingestor.getHaloClient().lookupHaloTicket(ticketId);

            if (ticket == null) {
                return null;
            }

            String json = Utils.GSON.toJson(ticket);
            return Context.getCurrent().eval("js", "(" + json + ")");
        } catch (Exception ex) {
            throw new ScriptException("Failed to lookup ticket: " + ex.getMessage(), ex);
        }
    }
}
