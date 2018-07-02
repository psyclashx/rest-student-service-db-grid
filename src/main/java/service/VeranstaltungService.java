package service;

import app.OTHRestException;
import app.Server;
import com.hazelcast.core.ICompletableFuture;
import com.hazelcast.core.IMap;
import com.hazelcast.mapreduce.Job;
import com.hazelcast.mapreduce.JobTracker;
import com.hazelcast.mapreduce.KeyValueSource;
import de.othr.vs.xml.Veranstaltung;
import mapreduce.TippCollator;
import mapreduce.TippMapper;
import mapreduce.TippReducerFactory;

import javax.ws.rs.*;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Path("veranstaltungen")
public class VeranstaltungService {

    static final String EVENTS_MAP_NAME = "veranstaltungen";

    @POST
    public String addEvent(Veranstaltung v) {

        IMap<String, Veranstaltung> events = Server.hazelcast.getMap(EVENTS_MAP_NAME);

        v.setId(UUID.randomUUID().toString());
        events.put(v.getId(), v);

        return v.getId();
    }

    @GET
    @Path("{eventid}")
    public Veranstaltung getEvent(@PathParam("eventid") String id) {

        IMap<String, Veranstaltung> events = Server.hazelcast.getMap(EVENTS_MAP_NAME);

        return events.get(id);

    }

    @GET
    public List<Veranstaltung> getEventsByQuery(@QueryParam("tipps") String tipps) {

        if(tipps == null || "".equals(tipps))
            throw new OTHRestException(405, "Please specify a 'tipps' query param");

        String[] suchwoerter = tipps.split("\\+"); // explode by +

        IMap<String, Veranstaltung> veranstaltungen = Server.hazelcast.getMap(EVENTS_MAP_NAME);


        // über Hazelcast-MapReduce-Algorithmus nach Veranstaltungen suchen,
        // die im Titel oder in der Beschreibung mind. eines der Suchwoerter enthalten


        JobTracker jobTracker = Server.hazelcast.getJobTracker("tippsJobTracker");
        KeyValueSource<String, Veranstaltung> source = KeyValueSource.fromMap(veranstaltungen);

        Job<String, Veranstaltung> job = jobTracker.newJob(source);


        ICompletableFuture<List<Veranstaltung>> future = job
                .mapper(new TippMapper(Arrays.asList(suchwoerter)))
                .reducer(new TippReducerFactory())
                .submit(new TippCollator());


        try {
            List<Veranstaltung> liste;
            liste = future.get(10, TimeUnit.SECONDS);
            return liste;
        } catch (InterruptedException | TimeoutException ex) {
            throw new OTHRestException(503, "Your query timed out");
        } catch (ExecutionException ex) {
            throw new OTHRestException(500, "Error while running your query request for tipps: " + tipps);
        }

    }
}
