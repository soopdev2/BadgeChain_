/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 *
 * @author Salvatore
 */
@Entity
@Table(name = "infotrack")
public class InfoTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String azione;

    private String descrizione;

    private String client_id;

    private LocalDateTime dataEvento;

    @ManyToOne
    @JoinColumn(name = "utente_id")
    private Utente utente;

    @ManyToOne
    @JoinColumn(name = "utente_id_selezionato")
    private Utente utente_selezionato;

    public InfoTrack() {
    }

    public InfoTrack(String azione, String descrizione, Utente utente, Utente utente_selezionato, String client_id) {
        this.azione = azione;
        this.descrizione = descrizione;
        this.dataEvento = LocalDateTime.now();
        this.utente = utente;
        this.utente_selezionato = utente_selezionato;
        this.client_id = client_id;
    }

    public Long getId() {
        return id;
    }

    public String getAzione() {
        return azione;
    }

    public void setAzione(String azione) {
        this.azione = azione;
    }

    public String getDescrizione() {
        return descrizione;
    }

    public void setDescrizione(String descrizione) {
        this.descrizione = descrizione;
    }

    public LocalDateTime getDataEvento() {
        return dataEvento;
    }

    public void setDataEvento(LocalDateTime dataEvento) {
        this.dataEvento = dataEvento;
    }

    public Utente getUtente() {
        return utente;
    }

    public void setUtente(Utente utente) {
        this.utente = utente;
    }

    public Utente getUtente_selezionato() {
        return utente_selezionato;
    }

    public void setUtente_selezionato(Utente utente_selezionato) {
        this.utente_selezionato = utente_selezionato;
    }

    public String getClient_id() {
        return client_id;
    }

    public void setClient_id(String client_id) {
        this.client_id = client_id;
    }

}
