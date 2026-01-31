package org.htmx;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/naval-radar") // Este será o endereço no seu navegador
public class NavalResource {

    @Inject
    @Location("naval-radar.html")
    Template navalRadar; // O Quarkus procura automaticamente por 'naval-radar.html' em src/main/resources/templates

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance getRadar() {
        // Renderiza a página inicial do radar
        return navalRadar.instance();
    }
}
