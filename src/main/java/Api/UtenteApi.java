/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package Api;

import DTO.UtenteDTO;
import ENUM.TipoAccessoEnum;
import Entity.InfoTrack;
import Entity.Ruolo;
import Entity.Utente;
import Services.Filter.Secured;
import Utility.JpaUtil;
import static Utility.JpaUtil.trovaUtenteById;
import static Utility.Utils.tryParseLong;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Salvatore
 */
@Path("/utente")
public class UtenteApi {
    
    @POST
    @Path("/trovaUtenti")
    @Secured
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response trovaTuttiGliUtenti(@QueryParam("user_id") String user_id_param) {
        InfoTrack infoTrack = new InfoTrack();
        infoTrack.setDataEvento(LocalDateTime.now());
        infoTrack.setAzione("API POST /utente/trovaUtenti - Ricerca lista completa utenti");
        
        try {
            if (user_id_param != null) {
                Long user_id = tryParseLong(user_id_param);
                Utente utente = trovaUtenteById(user_id);
                
                if (utente != null) {
                    infoTrack.setUtente(utente);
                    if (utente.getRuolo().getTipo().equals(TipoAccessoEnum.ADMIN)) {
                        
                        List<Utente> utenti = JpaUtil.trovaUtenti();
                        List<Map<String, Object>> utentiResponse = new ArrayList<>();
                        
                        if (utenti != null && !utenti.isEmpty()) {
                            for (Utente u : utenti) {
                                Map<String, Object> utenteData = new HashMap<>();
                                utenteData.put("id", u.getId());
                                switch (u.getStato()) {
                                    case 0 ->
                                        utenteData.put("stato", "DISABILITATO");
                                    case 1 ->
                                        utenteData.put("stato", "ABILITATO");
                                    default ->
                                        utenteData.put("stato", "NON DISPONIBILE");
                                }
                                utenteData.put("nome", (u.getName() != null) ? u.getName() : "non disponibile");
                                utenteData.put("ruolo", (u.getRuolo() != null && u.getRuolo().getTipo() != null)
                                        ? u.getRuolo().getTipo() : "non disponibile");
                                utenteData.put("url", (u.getUrl() != null) ? u.getUrl() : "non disponibile");
                                
                                utentiResponse.add(utenteData);
                            }
                        }
                        
                        infoTrack.setDescrizione("SUCCESSO - 200 - Ricerca utenti completata con successo.");
                        JpaUtil.salvaInfoTrack(infoTrack);
                        return Response.ok(utentiResponse).build();
                        
                    } else {
                        infoTrack.setDescrizione("ERRORE - 401 - Ricerca fallita: ruolo non autorizzato.");
                        JpaUtil.salvaInfoTrack(infoTrack);
                        return Response.status(Response.Status.UNAUTHORIZED)
                                .entity(Map.of("error", "Ruolo non autorizzato."))
                                .build();
                    }
                } else {
                    infoTrack.setDescrizione("ERRORE - 404 - Utente richiedente non trovato.");
                    JpaUtil.salvaInfoTrack(infoTrack);
                    return Response.status(Response.Status.NOT_FOUND)
                            .entity(Map.of("error", "Utente richiedente non trovato."))
                            .build();
                }
            } else {
                infoTrack.setDescrizione("ERRORE - 400 - Parametro user_id mancante o non valido.");
                JpaUtil.salvaInfoTrack(infoTrack);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "ID utente mancante o non valido nella richiesta."))
                        .build();
            }
            
        } catch (Exception e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore interno: " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Errore interno durante la ricerca degli utenti: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/trovaUtente")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Secured
    public Response trovaUtente(@QueryParam("user_id") String user_id_param,
            @QueryParam("selected_user_id") String selected_user_id_param) {
        InfoTrack infoTrack = new InfoTrack();
        infoTrack.setDataEvento(LocalDateTime.now());
        infoTrack.setAzione("API POST /utente/trovaUtente - Ricerca singolo utente");
        
        try {
            if (user_id_param != null) {
                if (selected_user_id_param != null) {
                    Long userId = tryParseLong(user_id_param);
                    Long selected_user_id = tryParseLong(selected_user_id_param);
                    Utente utente = JpaUtil.trovaUtenteById(userId);
                    Utente utente_selezionato = JpaUtil.trovaUtenteById(selected_user_id);
                    
                    if (utente != null) {
                        infoTrack.setUtente(utente);
                        if ((utente.getRuolo().getTipo().equals(TipoAccessoEnum.ADMIN))
                                || (utente.getRuolo().getTipo().equals(TipoAccessoEnum.UTENTE) && utente.getId().equals(selected_user_id))) {
                            
                            if (utente_selezionato != null) {
                                infoTrack.setUtente_selezionato(utente_selezionato);
                                Map<String, Object> utenteData = new HashMap<>();
                                utenteData.put("id", utente_selezionato.getId());
                                utenteData.put("nome", utente_selezionato.getName());
                                utenteData.put("ruolo", utente_selezionato.getRuolo().getTipo().name());
                                utenteData.put("url", utente_selezionato.getUrl());
                                
                                switch (utente_selezionato.getStato()) {
                                    case 0 ->
                                        utenteData.put("stato", "DISABILITATO");
                                    case 1 ->
                                        utenteData.put("stato", "ABILITATO");
                                    default ->
                                        utenteData.put("stato", "NON DISPONIBILE");
                                }
                                
                                infoTrack.setDescrizione("SUCCESSO - 200 - Utente trovato con successo: ID " + selected_user_id + ".");
                                JpaUtil.salvaInfoTrack(infoTrack);
                                return Response.ok(utenteData).build();
                                
                            } else {
                                infoTrack.setDescrizione("ERRORE - 404 - Utente selezionato non trovato: id - " + selected_user_id + ".");
                                JpaUtil.salvaInfoTrack(infoTrack);
                                return Response.status(Response.Status.NOT_FOUND)
                                        .entity(Map.of("error", "Utente selezionato non trovato."))
                                        .build();
                            }
                            
                        } else {
                            infoTrack.setDescrizione("ERRORE - 401 - Ricerca fallita: ruolo non autorizzato.");
                            JpaUtil.salvaInfoTrack(infoTrack);
                            return Response.status(Response.Status.UNAUTHORIZED)
                                    .entity(Map.of("error", "Ruolo non autorizzato."))
                                    .build();
                        }
                    } else {
                        infoTrack.setDescrizione("ERRORE - 404 - Utente richiedente non trovato.");
                        JpaUtil.salvaInfoTrack(infoTrack);
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity(Map.of("error", "Utente richiedente non trovato."))
                                .build();
                    }
                } else {
                    infoTrack.setDescrizione("ERRORE - 400 - Parametro selected_user_id mancante.");
                    JpaUtil.salvaInfoTrack(infoTrack);
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "ID utente o ID selezionato mancanti."))
                            .build();
                }
            } else {
                infoTrack.setDescrizione("ERRORE - 400 - Parametro user_id mancante.");
                JpaUtil.salvaInfoTrack(infoTrack);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "ID utente o ID selezionato mancanti."))
                        .build();
            }
            
        } catch (Exception e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore interno: " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Errore durante la ricerca dell'utente: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/crea")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Secured
    public Response creaUtente(@QueryParam("user_id") String user_id_param, UtenteDTO utenteDTO) {
        InfoTrack infoTrack = new InfoTrack();
        infoTrack.setDataEvento(LocalDateTime.now());
        infoTrack.setAzione("API POST /utente/crea - Creazione utente");
        
        try {
            if (user_id_param != null) {
                Long userId = tryParseLong(user_id_param);
                Utente utente = JpaUtil.trovaUtenteById(userId);
                
                if (utente != null) {
                    infoTrack.setUtente(utente);
                    if (utente.getRuolo().getTipo().equals(TipoAccessoEnum.ADMIN)) {
                        Utente nuovoUtente = new Utente();
                        if (utenteDTO.getNome() != null) {
                            nuovoUtente.setName(utenteDTO.getNome());
                        } else {
                            nuovoUtente.setName("non inserito");
                        }
                        if (utenteDTO.getEmail() != null) {
                            nuovoUtente.setEmail(utenteDTO.getEmail());
                        } else {
                            nuovoUtente.setEmail("non inserito");
                        }
                        
                        if (utenteDTO.getUrl() != null) {
                            nuovoUtente.setUrl(utenteDTO.getUrl());
                        } else {
                            nuovoUtente.setUrl("non inserito");
                        }
                        
                        Ruolo ruolo = JpaUtil.trovaRuoloById(utenteDTO.getRuolo());
                        if (ruolo == null) {
                            infoTrack.setDescrizione("ERRORE - 400 - Creazione fallita: ruolo non trovato.");
                            JpaUtil.salvaInfoTrack(infoTrack);
                            return Response.status(Response.Status.BAD_REQUEST)
                                    .entity(Map.of("error", "Ruolo specificato non trovato."))
                                    .build();
                        }
                        
                        nuovoUtente.setRuolo(ruolo);
                        Utente utenteSalvato = JpaUtil.salvaUtente(nuovoUtente);
                        
                        infoTrack.setDescrizione("SUCCESSO - 200 - Utente creato con successo: ID " + utenteSalvato.getId() + ".");
                        infoTrack.setUtente_selezionato(utenteSalvato);
                        JpaUtil.salvaInfoTrack(infoTrack);
                        return Response.status(Response.Status.CREATED)
                                .entity(Map.of("message", "Utente creato con successo.", "id", utenteSalvato.getId()))
                                .build();
                        
                    } else {
                        infoTrack.setDescrizione("ERRORE - 401 - Creazione fallita: ruolo non autorizzato");
                        JpaUtil.salvaInfoTrack(infoTrack);
                        return Response.status(Response.Status.UNAUTHORIZED)
                                .entity(Map.of("error", "Ruolo non autorizzato"))
                                .build();
                    }
                    
                } else {
                    infoTrack.setDescrizione("ERRORE - 404 - Utente richiedente non trovato.");
                    JpaUtil.salvaInfoTrack(infoTrack);
                    return Response.status(Response.Status.NOT_FOUND)
                            .entity(Map.of("error", "Utente richiedente non trovato."))
                            .build();
                }
            } else {
                infoTrack.setDescrizione("ERRORE - 400 - Parametro user_id mancante o non valido.");
                JpaUtil.salvaInfoTrack(infoTrack);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "ID utente mancante o non valido."))
                        .build();
            }
            
        } catch (Exception e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore interno: " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Errore durante la creazione dell'utente: " + e.getMessage()))
                    .build();
        }
    }
    
    @PUT
    @Path("/modifica")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Secured
    public Response aggiornaUtente(@QueryParam("user_id") String user_id_param,
            @QueryParam("selected_user_id") String selected_user_id_param,
            UtenteDTO utenteDTO) {
        
        InfoTrack infoTrack = new InfoTrack();
        infoTrack.setDataEvento(LocalDateTime.now());
        infoTrack.setAzione("API PUT /utente/modifica - Modifica utente");
        
        try {
            if (user_id_param != null) {
                if (selected_user_id_param != null) {
                    Long userId = tryParseLong(user_id_param);
                    Long selected_user_id = tryParseLong(selected_user_id_param);
                    Utente utente = JpaUtil.trovaUtenteById(userId);
                    
                    if (utente != null) {
                        infoTrack.setUtente(utente);
                        if (utente.getRuolo().getTipo().equals(TipoAccessoEnum.ADMIN)) {
                            Utente utenteEsistente = JpaUtil.trovaUtenteById(selected_user_id);
                            infoTrack.setUtente_selezionato(utenteEsistente);
                            
                            if (utenteEsistente == null) {
                                infoTrack.setDescrizione("ERRORE - 404 - Modifica fallita: utente selezionato non trovato.");
                                JpaUtil.salvaInfoTrack(infoTrack);
                                return Response.status(Response.Status.NOT_FOUND)
                                        .entity(Map.of("error", "Utente non trovato con ID " + selected_user_id))
                                        .build();
                            }
                            
                            if (utenteDTO.getNome() != null) {
                                utenteEsistente.setName(utenteDTO.getNome());
                            }
                            
                            if (utenteDTO.getEmail() != null) {
                                utenteEsistente.setEmail(utenteDTO.getEmail());
                            }
                            
                            if (utenteDTO.getUrl() != null) {
                                utenteEsistente.setUrl(utenteDTO.getUrl());
                            }
                            
                            if (utenteDTO.getRuolo() != null) {
                                Ruolo ruolo = JpaUtil.trovaRuoloById(utenteDTO.getRuolo());
                                utenteEsistente.setRuolo(ruolo);
                            } else {
                                infoTrack.setDescrizione("ERRORE - 400 - Modifica fallita: ruolo mancante o non valido.");
                                JpaUtil.salvaInfoTrack(infoTrack);
                                return Response.status(Response.Status.BAD_REQUEST)
                                        .entity(Map.of("error", "Ruolo mancante o non valido."))
                                        .build();
                            }
                            
                            JpaUtil.aggiornaUtente(utenteEsistente);
                            
                            infoTrack.setDescrizione("SUCCESSO - 200 - Utente aggiornato con successo: ID " + selected_user_id + ".");
                            JpaUtil.salvaInfoTrack(infoTrack);
                            return Response.ok(Map.of("message", "Utente aggiornato con successo.", "id", selected_user_id))
                                    .build();
                            
                        } else {
                            infoTrack.setDescrizione("ERRORE - 401 - Aggiornamento fallito: ruolo non autorizzato.");
                            JpaUtil.salvaInfoTrack(infoTrack);
                            return Response.status(Response.Status.UNAUTHORIZED)
                                    .entity(Map.of("error", "Ruolo non autorizzato"))
                                    .build();
                        }
                        
                    } else {
                        infoTrack.setDescrizione("ERRORE - 404 - Utente richiedente non trovato.");
                        JpaUtil.salvaInfoTrack(infoTrack);
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity(Map.of("error", "Utente richiedente non trovato."))
                                .build();
                    }
                    
                } else {
                    infoTrack.setDescrizione("ERRORE - 400 - Parametro selected_user_id mancante.");
                    JpaUtil.salvaInfoTrack(infoTrack);
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "ID utente selezionato mancante."))
                            .build();
                }
            } else {
                infoTrack.setDescrizione("ERRORE - 400 - Parametro user_id mancante.");
                JpaUtil.salvaInfoTrack(infoTrack);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "ID utente richiedente mancante."))
                        .build();
            }
            
        } catch (Exception e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore durante la modifica: " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Errore durante la modifica dell'utente: " + e.getMessage()))
                    .build();
        }
    }
    
    @DELETE
    @Path("/elimina")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Secured
    public Response eliminaUtente(@QueryParam("user_id") String user_id_param,
            @QueryParam("selected_user_id") String selected_user_id_param) {
        
        InfoTrack infoTrack = new InfoTrack();
        infoTrack.setDataEvento(LocalDateTime.now());
        infoTrack.setAzione("API DELETE /utente/elimina - Eliminazione utente");
        
        try {
            if (user_id_param != null) {
                if (selected_user_id_param != null) {
                    Long userId = tryParseLong(user_id_param);
                    Long selected_user_id = tryParseLong(selected_user_id_param);
                    Utente utente = trovaUtenteById(userId);
                    
                    if (utente != null) {
                        infoTrack.setUtente(utente);
                        if (utente.getRuolo().getTipo().equals(TipoAccessoEnum.ADMIN)) {
                            Utente utenteSelezionato = JpaUtil.trovaUtenteById(selected_user_id);
                            boolean deleted = JpaUtil.eliminaUtenteById(selected_user_id);
                            infoTrack.setUtente_selezionato(utenteSelezionato);
                            
                            if (deleted) {
                                infoTrack.setDescrizione("SUCCESSO - 200 - Utente eliminato con successo: ID " + selected_user_id + ".");
                                JpaUtil.salvaInfoTrack(infoTrack);
                                return Response.ok(Map.of("message", "Utente eliminato con successo.", "id", selected_user_id)).build();
                            } else {
                                infoTrack.setDescrizione("ERRORE - 404 - Eliminazione fallita: utente non trovato.");
                                JpaUtil.salvaInfoTrack(infoTrack);
                                return Response.status(Response.Status.NOT_FOUND)
                                        .entity(Map.of("error", "Utente non trovato con ID " + selected_user_id))
                                        .build();
                            }
                        } else {
                            infoTrack.setDescrizione("ERRORE - 401 - Eliminazione fallita: ruolo non autorizzato.");
                            JpaUtil.salvaInfoTrack(infoTrack);
                            return Response.status(Response.Status.UNAUTHORIZED)
                                    .entity(Map.of("error", "Ruolo non autorizzato"))
                                    .build();
                        }
                    } else {
                        infoTrack.setDescrizione("ERRORE - 404 - Utente richiedente non trovato.");
                        JpaUtil.salvaInfoTrack(infoTrack);
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity(Map.of("error", "Utente non trovato."))
                                .build();
                    }
                } else {
                    infoTrack.setDescrizione("ERRORE - 400 - Parametro selected_user_id mancante.");
                    JpaUtil.salvaInfoTrack(infoTrack);
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "ID utente selezionato mancante."))
                            .build();
                }
            } else {
                infoTrack.setDescrizione("ERRORE - 400 - Parametro user_id mancante.");
                JpaUtil.salvaInfoTrack(infoTrack);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "ID utente richiedente mancante."))
                        .build();
            }
            
        } catch (Exception e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore durante l'eliminazione: " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Errore durante l'eliminazione dell'utente: " + e.getMessage()))
                    .build();
        }
    }
    
    @POST
    @Path("/riabilita")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Secured
    public Response riabilitaUtente(@QueryParam("user_id") String user_id_param,
            @QueryParam("selected_user_id") String selected_user_id_param) {
        
        InfoTrack infoTrack = new InfoTrack();
        infoTrack.setDataEvento(LocalDateTime.now());
        infoTrack.setAzione("API POST /utente/riabilita - Riabilitazione utente");
        
        try {
            if (user_id_param != null) {
                if (selected_user_id_param != null) {
                    Long userId = tryParseLong(user_id_param);
                    Long selected_user_id = tryParseLong(selected_user_id_param);
                    Utente utente = trovaUtenteById(userId);
                    
                    if (utente != null) {
                        infoTrack.setUtente(utente);
                        if (utente.getRuolo().getTipo().equals(TipoAccessoEnum.ADMIN)) {
                            Utente utente_selezionato = trovaUtenteById(selected_user_id);
                            infoTrack.setUtente_selezionato(utente_selezionato);
                            
                            if (utente_selezionato == null) {
                                infoTrack.setDescrizione("ERRORE - 404 - Riabilitazione fallita: utente selezionato non trovato.");
                                JpaUtil.salvaInfoTrack(infoTrack);
                                return Response.status(Response.Status.NOT_FOUND)
                                        .entity(Map.of("error", "Utente selezionato non trovato."))
                                        .build();
                            }
                            
                            if (utente_selezionato.getStato() == 0) {
                                boolean riabilitato = JpaUtil.riattivaUtenteById(selected_user_id);
                                
                                if (riabilitato) {
                                    infoTrack.setDescrizione("SUCCESSO - 200 - Utente riabilitato con successo: ID " + selected_user_id + ".");
                                    JpaUtil.salvaInfoTrack(infoTrack);
                                    return Response.ok(Map.of("message", "Utente riabilitato con successo.", "id", selected_user_id)).build();
                                } else {
                                    infoTrack.setDescrizione("ERRORE - 404 - Riabilitazione fallita: utente non trovato.");
                                    JpaUtil.salvaInfoTrack(infoTrack);
                                    return Response.status(Response.Status.NOT_FOUND)
                                            .entity(Map.of("error", "Utente non trovato con ID " + selected_user_id))
                                            .build();
                                }
                            } else {
                                infoTrack.setDescrizione("ERRORE - 400 - Riabilitazione fallita: utente non disabilitato.");
                                JpaUtil.salvaInfoTrack(infoTrack);
                                return Response.status(Response.Status.BAD_REQUEST)
                                        .entity(Map.of("error", "L'utente non risulta disabilitato."))
                                        .build();
                            }
                        } else {
                            infoTrack.setDescrizione("ERRORE - 401 - Riabilitazione fallita: ruolo non autorizzato.");
                            JpaUtil.salvaInfoTrack(infoTrack);
                            return Response.status(Response.Status.UNAUTHORIZED)
                                    .entity(Map.of("error", "Ruolo non autorizzato."))
                                    .build();
                        }
                    } else {
                        infoTrack.setDescrizione("ERRORE - 404 - Utente richiedente non trovato.");
                        JpaUtil.salvaInfoTrack(infoTrack);
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity(Map.of("error", "Utente non trovato."))
                                .build();
                    }
                } else {
                    infoTrack.setDescrizione("ERRORE - 400 - Parametro selected_user_id mancante.");
                    JpaUtil.salvaInfoTrack(infoTrack);
                    return Response.status(Response.Status.BAD_REQUEST)
                            .entity(Map.of("error", "ID utente selezionato mancante."))
                            .build();
                }
            } else {
                infoTrack.setDescrizione("ERRORE - 400 - Parametro user_id mancante.");
                JpaUtil.salvaInfoTrack(infoTrack);
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "ID utente richiedente mancante."))
                        .build();
            }
            
        } catch (Exception e) {
            infoTrack.setDescrizione("ERRORE - 500 - Errore durante la riabilitazione: " + e.getMessage());
            JpaUtil.salvaInfoTrack(infoTrack);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "Errore durante la riabilitazione dell'utente: " + e.getMessage()))
                    .build();
        }
    }
    
}
