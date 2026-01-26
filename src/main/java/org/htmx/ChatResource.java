package org.htmx;

import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;

@Path("/")
public class ChatResource {

    @Inject
    Template chat;

    @GET
    @Produces("text/html")
    public TemplateInstance getChat() {
        return chat.instance();
    }
}