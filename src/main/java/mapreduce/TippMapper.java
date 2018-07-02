package mapreduce;

import com.hazelcast.mapreduce.Context;
import com.hazelcast.mapreduce.Mapper;
import de.othr.vs.xml.Veranstaltung;

import java.util.Date;
import java.util.List;

public class TippMapper implements Mapper<String, Veranstaltung, String, Veranstaltung> {

    private List<String> suchwoerter;
    private Date jetzt = new Date();

    public TippMapper(List<String> suchwoerter) {
        this.suchwoerter = suchwoerter;
    }
    
    @Override
    public void map(String key, Veranstaltung v, Context<String, Veranstaltung> context) {
        
        if(v.getEnde().before(jetzt)) // in case event is in the past, ignore
            return;
        
        for(String suchwort : suchwoerter) {
            if(v.getBeschreibung().toLowerCase().contains(suchwort.toLowerCase())
                || v.getTitel().toLowerCase().contains(suchwort.toLowerCase())) {
                context.emit(suchwort, v);
            }
        }
    }
    
}
