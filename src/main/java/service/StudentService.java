package service;

import app.OTHRestException;
import app.Server;
import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.ReplicatedMap;
import de.othr.vs.xml.Adresse;
import de.othr.vs.xml.Student;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static app.Server.*;

@Path("studentaffairs")  // alternativer Pfad: @Path("studentaffairs/students") --> ersetzt dann alle @Path-Annotationen unten
public class StudentService {

    static final String STUDENTS_MAP_NAME = "students";
    static final IAtomicLong highestId = hazelcast.getAtomicLong("latest-student-id");


    @POST
    @Path("students")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public Student matriculate(Student s) {

        Connection c = null;
        ReplicatedMap<Integer, Student> students = Server.hazelcast.getReplicatedMap(STUDENTS_MAP_NAME);

        try {
            c = DriverManager.getConnection(DB_CONNECTION, DB_USERNAME, DB_PASSWORD);
            Statement statement = c.createStatement();
            String query = "INSERT INTO Student (vorname, nachname, ects, strasse, ort) VALUES ('"
                    + s.getVorname() + "', '"
                    + s.getNachname() + "', 0.0, '" +
                    s.getAnschrift().getStrasse() + "', '" +
                    s.getAnschrift().getOrt();
            statement.executeUpdate(query);
            /*
            // neue Matrikelnr über AUTO-INCREMENT-FEATURE der Datenbank
            ResultSet primaryKey = statement.getGeneratedKeys();
            primaryKey.first();
            int matrikelNr = primaryKey.getInt(1);
             */

            // Zusatzfrage: Hazelcast-Datenstruktur für neue Matrikelnr.
            // (kann aber im Testbetrieb zu "duplicate keys" in der Datenbank führen
            long matrikelNr = highestId.incrementAndGet();
            s.setMatrikelNr((int)matrikelNr);

            c.close();

            // zusätzlich in Hazelcast data grid hinzufügen (für 5 Minuten)
            students.put((int)matrikelNr, s, 5, TimeUnit.MINUTES);

            return s;

        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new OTHRestException(500, "Fehler während Datenbankabfrage: " + ex.getMessage());
        }

    }

    @DELETE
    @Path("students/{id}")
    public Student exmatriculate(@PathParam("id") int studentId) {

        Student s = null;
        Connection c = null;

        Student found = getStudentById(studentId);
        try {
            c = DriverManager.getConnection(DB_CONNECTION, DB_USERNAME, DB_PASSWORD);
            Statement statement = c.createStatement();
            String query = "DELETE FROM Student WHERE matrikelNr = " + studentId;
            int results = statement.executeUpdate(query);
            c.close();
            if (results != 1) {
                throw new OTHRestException(404, "Student mit ID " + studentId + " ist nicht immatrikuliert");
            }
            return s;

        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new OTHRestException(500, "Fehler während Datenbankabfrage: " + ex.getMessage());
        }

    }

    @GET
    @Path("students/{id}")
    public Student getStudentById(@PathParam("id") int studentId) {

        Student s = null;
        Connection c = null;
        ReplicatedMap<Integer, Student> students = Server.hazelcast.getReplicatedMap(STUDENTS_MAP_NAME);

        // Falls in Hazelcast verfügbar...
        s = students.get(studentId);
        if(s != null)
            return s;

        // ...ansonsten aus Datenbank lesen...
        try {
            c = DriverManager.getConnection(DB_CONNECTION, DB_USERNAME, DB_PASSWORD);
            Statement statement = c.createStatement();
            String query = "SELECT vorname, nachname, ects, strasse, ort FROM Student WHERE matrikelNr=" + studentId;
            ResultSet result = statement.executeQuery(query);
            if (result.first()) {
                s = new Student(
                        studentId,
                        result.getString("vorname"),
                        result.getString("nachname"),
                        result.getInt("ects"),
                        new Adresse(
                                result.getString("strasse"),
                                result.getString("ort")
                        ));
            } else {
                throw new OTHRestException(404, "Student mit ID " + studentId + " ist nicht immatrikuliert");
            }

            c.close();

            //...und aber im data grid auch veröffentlichen (für 5 Minuten)
            students.put(s.getMatrikelNr(), s, 5, TimeUnit.MINUTES);

            return s;

        } catch (SQLException ex) {
            throw new OTHRestException(500, "Fehler während Datenbankabfrage: " + ex.getMessage());
        }

    }

    @PUT
    @Path("students/{id}")
    public Student updateStudentAccount(@PathParam("id") int studentId, Student newData) {

        if(studentId != newData.getMatrikelNr())
            throw new OTHRestException(409, "Matrikelnummer kann nicht geändert werden");  // CONFLICT

        Connection c = null;

        Student found = getStudentById(studentId);

        if(found.getEcts() != newData.getEcts())
            throw new OTHRestException(400, "ECTS können nur durch Eintragung einer neuen Prüfungsleistung geändert werden");  // BAD REQUEST

        if("".equals(newData.getVorname()) || newData.getVorname() == null
                || "".equals(newData.getNachname()) || newData.getNachname() == null
                || "".equals(newData.getAnschrift().getStrasse()) || newData.getAnschrift().getStrasse() == null
                || "".equals(newData.getAnschrift().getOrt()) || newData.getAnschrift().getOrt() == null )
            throw new OTHRestException(400, "Vorname, Nachname, Strasse und Ort dürfen nicht leer sein");

        try {
            c = DriverManager.getConnection(DB_CONNECTION, DB_USERNAME, DB_PASSWORD);
            Statement statement = c.createStatement();
            String query = "UPDATE Student SET vorname = " + newData.getVorname() +
                    ", nachname = " + newData.getNachname() +
                    ", strasse = " + newData.getAnschrift().getStrasse() + ", ort = " +
                    "" + newData.getAnschrift().getOrt() +
                    " WHERE matrikelNr = " + studentId;
            int results = statement.executeUpdate(query);
            c.close();
            if (results != 1) {
                throw new OTHRestException(404, "Fehler beim Ändern der Daten zu ID " + studentId + " (" + results + ")");
            }
            return newData;

        } catch (SQLException ex) {
            ex.printStackTrace();
            throw new OTHRestException(500, "Fehler während Datenbankabfrage: " + ex.getMessage());
        }

    }

    // Diese Methode hätte eigentlich folgende Annotationen:
    // @GET
    // @Path("students")
    // Diese sind jedoch identisch mit den Annotationen von getStudentsByRange (s. u.)
    private Collection<Student> getAllStudents(String query) {

        Connection c = null;

        try {
            c = DriverManager.getConnection(DB_CONNECTION, DB_USERNAME, DB_PASSWORD);
            Statement statement = c.createStatement();

            ResultSet result = statement.executeQuery(query);

            List<Student> students = new LinkedList<>();
            while (result.next()) {
                Student s = new Student();
                s.setMatrikelNr(result.getInt("matrikelNr"));
                s.setVorname(result.getString("vorname"));
                s.setNachname(result.getString("nachname"));
                s.setEcts(result.getInt("ects"));
                Adresse anschrift = new Adresse(
                                result.getString("strasse"),
                                result.getString("ort")
                        );
                s.setAnschrift(anschrift);
                students.add(s);
            }
            return students;

        } catch (SQLException ex) {
            throw new OTHRestException(500, "Fehler während Datenbankabfrage: " + ex.getMessage());
        }

    }

    @GET
    @Path("students")
    public Collection<Student> getStudentsByRange(@QueryParam("from") int fromStudentId, @QueryParam("to") int toStudentId) {

        String query = "SELECT matrikelNr, vorname, nachname, ects, strasse, ort FROM Student";
        /* Beispiele für mögliche Resource-Pfade zum Aufruf dieser Methode:

              /restapi/studentaffairs/students?from=100&to=108
              /restapi/studentaffairs/students?from=100
              /restapi/studentaffairs/students?to=108
              /restapi/studentaffairs/students

              Die Angabe der Query-Parameter "from" und "to" ist also nicht zwingend erforderlich.
              Werden Sie weggelassen wird entsprechend der Wert 0 übergeben
         */
        if(fromStudentId == 0 && toStudentId == 0)
            return getAllStudents(query);
        else if(toStudentId == 0 && fromStudentId > 0)
            return getAllStudents(query + " WHERE matrikelNr >= " + fromStudentId);
        else
            return getAllStudents(query + " WHERE matrikelNr >= " + fromStudentId + " AND matrikelNr <= " + toStudentId);

    }
}
