package app;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;

@Provider
public class ServerExceptionMapper implements ExceptionMapper<Throwable> {

    // Klasse fängt alle internen Fehler ab, gibt sie auf der Server-Konsole aus und
    // sendet sie auch an den Client (über Response-Objekt)


    @Override
    public Response toResponse(Throwable t) {

        if(t instanceof OTHRestException) {
            OTHRestException exception = (OTHRestException)t;
            return Response.status(exception.getHttpStatuscode())
                    .type(MediaType.TEXT_PLAIN)
                    .entity(exception.getMessage())
                    .build();
        } else {
            t.printStackTrace();

            return Response.serverError()
                    .entity(t.getMessage())
                    .build();
        }
    }
}