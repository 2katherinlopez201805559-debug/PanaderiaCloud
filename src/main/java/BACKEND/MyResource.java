package BACKEND;



import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import BACKEND.utils.jsonResult;


@Path("myresource")
public class MyResource {


    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getIt() {
        return "Got it!";
    }
	
    
    @GET
    @Path("/json")
    @Produces("application/json")
    public Response json() {
		try {
			jsonResult salida = new jsonResult();
			salida.id=1;
			salida.result="OK";
			salida.msj="Estoy list para hacer servicios Rest";
			return Response.ok(salida).build();
		} catch (Exception e) {
			e.printStackTrace(); 
			return Response.ok(new jsonResult(-1,"Error",e.getMessage())).build();
		}
    }
    

	
}
