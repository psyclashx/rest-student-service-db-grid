package mapreduce;

import com.hazelcast.mapreduce.Collator;
import de.othr.vs.xml.Veranstaltung;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class TippCollator implements Collator<Map.Entry<String, List<Veranstaltung>>, List<Veranstaltung>> {

    Map<Veranstaltung, Integer> gewichteteGesamtliste = new HashMap<>();
    
    @Override
    public List<Veranstaltung> collate(Iterable<Map.Entry<String, List<Veranstaltung>>> alleListenFuerAlleKeywords) {

        for(Map.Entry<String, List<Veranstaltung>> entry : alleListenFuerAlleKeywords) {
            for(Veranstaltung v : entry.getValue()) {
                if(gewichteteGesamtliste.containsKey(v)) {
                    gewichteteGesamtliste.replace(v, gewichteteGesamtliste.get(v).intValue() + 1);
                } else {
                    gewichteteGesamtliste.put(v, 1);
                }
            }
        }
        
        List<Map.Entry<Veranstaltung, Integer>> liste = new ArrayList<>(gewichteteGesamtliste.entrySet());
        
        // Lambda-Ausdruck
        // (Fast) gleichbedeutend mit anonymer Klasse: liste.sort( new Comparator<Map.Entry>() { @Override public int compare(...) { ... } } );
        liste.sort((entry1, entry2) -> { 
            if( entry1.getValue().intValue() == entry2.getValue().intValue() ) {
                return entry1.getKey().getEnde().compareTo(entry2.getKey().getEnde());
            }
            else
                return -Integer.compare(entry1.getValue().intValue(), entry2.getValue().intValue()); // - wegen umgekehrter Reihenfolge
        });
        List<Veranstaltung> ergebnis = new LinkedList<>();

        // Lambda-Ausdruck (statt for(each)-Schleife )
        liste.stream().forEachOrdered((v) -> {
            ergebnis.add(v.getKey());
        });

        return new LinkedList(ergebnis);
    }
    
}
