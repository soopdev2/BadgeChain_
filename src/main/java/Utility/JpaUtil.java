/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Utility;

import Entity.InfoTrack;
import Entity.Ruolo;
import Entity.Utente;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import jakarta.persistence.Query;
import java.time.LocalDateTime;
import java.util.List;

/**
 *
 * @author Salvatore
 */
public class JpaUtil {

    final static EntityManagerFactory emf = Persistence.createEntityManagerFactory("openbadge");
    final static EntityManager em = emf.createEntityManager();

    public static List<Utente> trovaUtenti() {
        InfoTrack infotrack = new InfoTrack();
        infotrack.setDataEvento(LocalDateTime.now());
        infotrack.setAzione("JpaUtil - trovaUtenti() - recupera lista completa utenti.");
        try {
            Query query = em.createQuery("SELECT u FROM Utente u", Utente.class);
            List<Utente> utenti = query.getResultList();
            infotrack.setDescrizione("SUCCESSO - 200 - Lista completa utenti recuperata con successo.");
            salvaInfoTrack(infotrack);
            return utenti.isEmpty() ? null : utenti;

        } catch (Exception e) {
            infotrack.setDescrizione("ERRORE - 500 - Non è stato possibile recuperare lista completa utenti : " + e.getMessage());
            salvaInfoTrack(infotrack);
        }
        return null;
    }

    public static Utente trovaUtenteById(Long id) {
        InfoTrack infotrack = new InfoTrack();
        infotrack.setDataEvento(LocalDateTime.now());
        infotrack.setAzione("JpaUtil - trovaUtenteById() - recupera singolo utente.");
        try {
            infotrack.setDescrizione("SUCCESSO - 200 - Singolo utente con id " + id + " recuperato con successo.");
            salvaInfoTrack(infotrack);
            return em.find(Utente.class, id);
        } catch (Exception e) {
            infotrack.setDescrizione("ERRORE - 500 - Non è stato possibile recuperare il singolo utente con id " + id + " : " + e.getMessage());
            salvaInfoTrack(infotrack);
        }
        return null;
    }

    public static Ruolo trovaRuoloById(Long id) {
        InfoTrack infotrack = new InfoTrack();
        infotrack.setDataEvento(LocalDateTime.now());
        infotrack.setAzione("JpaUtil - trovaRuoloById() - recupera ruolo.");
        try {
            infotrack.setDescrizione("SUCCESSO - 200 - Ruolo con id " + id + " recuperato con successo.");
            salvaInfoTrack(infotrack);
            return em.find(Ruolo.class, id);
        } catch (Exception e) {
            infotrack.setDescrizione("ERRORE - 500 - Non è stato possibile recuperare il ruolo con id " + id + " : " + e.getMessage());
            salvaInfoTrack(infotrack);
        }
        return null;
    }

    public static Utente salvaUtente(Utente utente) {
        EntityTransaction tx = em.getTransaction();
        InfoTrack infotrack = new InfoTrack();
        infotrack.setDataEvento(LocalDateTime.now());
        infotrack.setAzione("JpaUtil - salvaUtente() - effettua creazione singolo utente.");
        try {
            tx.begin();
            utente.setStato(1);
            em.persist(utente);
            tx.commit();
            infotrack.setDescrizione("SUCCESSO - 200 - Creazione utente con id " + utente.getId() + " effettuata con successo.");
            salvaInfoTrack(infotrack);
            return utente;
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            infotrack.setDescrizione("ERRORE - 500 - Non è stato possibile effettuare la creazione dell'utente: " + e.getMessage());
            salvaInfoTrack(infotrack);
            return null;
        }
    }

    public static Utente aggiornaUtente(Utente utente) {
        EntityTransaction tx = em.getTransaction();
        InfoTrack infotrack = new InfoTrack();
        infotrack.setDataEvento(LocalDateTime.now());
        infotrack.setAzione("JpaUtil - aggiornaUtente() - effettua aggiornamento utente.");
        try {
            tx.begin();
            Utente utenteAggiornato = em.merge(utente);
            tx.commit();
            infotrack.setDescrizione("SUCCESSO - 200 - Aggiornamento utente con id " + utente.getId() + " effettuato con successo.");
            salvaInfoTrack(infotrack);
            return utenteAggiornato;
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            infotrack.setDescrizione("ERRORE - 500 - Non è stato possibile effettuare l'aggiornamento dell'utente con id " + utente.getId() + ": " + e.getMessage());
            salvaInfoTrack(infotrack);
            return null;
        }
    }

    public static boolean eliminaUtenteById(Long id) {
        EntityTransaction tx = em.getTransaction();
        InfoTrack infotrack = new InfoTrack();
        infotrack.setDataEvento(LocalDateTime.now());
        infotrack.setAzione("JpaUtil - eliminaUtenteById() - effettua eliminazione utente.");
        try {
            tx.begin();
            Utente utente = em.find(Utente.class, id);

            if (utente != null) {
                utente.setStato(0);
                em.merge(utente);
                tx.commit();
                infotrack.setDescrizione("SUCCESSO - 200 - Eliminazione utente con id " + id + " effettuata con successo.");
                salvaInfoTrack(infotrack);
                return true;
            } else {
                tx.rollback();
                return false;
            }
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            infotrack.setDescrizione("ERRORE - 500 - Non è stato possibile effettuare l'eliminazione dell'utente con id " + id + ": " + e.getMessage());
            salvaInfoTrack(infotrack);
            return false;
        }
    }

    public static boolean riattivaUtenteById(Long id) {
        EntityTransaction tx = em.getTransaction();
        InfoTrack infotrack = new InfoTrack();
        infotrack.setDataEvento(LocalDateTime.now());
        infotrack.setAzione("JpaUtil - riattivaUtenteById() - effettua riabilitazione utente.");
        try {
            tx.begin();
            Utente utente = em.find(Utente.class, id);

            if (utente != null) {
                utente.setStato(1);
                em.merge(utente);

                tx.commit();
                infotrack.setDescrizione("SUCCESSO - 200 - Riabilitazione utente con id " + id + " effettuata con successo.");
                salvaInfoTrack(infotrack);
                return true;
            } else {
                tx.rollback();
                return false;
            }
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            infotrack.setDescrizione("ERRORE - 500 - Non è stato possibile effettuare la riabilitazione dell'utente con id " + id + ": " + e.getMessage());
            salvaInfoTrack(infotrack);
            return false;
        }
    }

    public static InfoTrack salvaInfoTrack(InfoTrack InfoTrack) {
        EntityTransaction tx = em.getTransaction();
        try {
            tx.begin();
            em.persist(InfoTrack);
            tx.commit();
            return InfoTrack;
        } catch (Exception e) {
            if (tx != null && tx.isActive()) {
                tx.rollback();
            }
            System.out.println(e);
            return null;
        }
    }
}
