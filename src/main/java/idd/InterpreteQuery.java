package idd;

import java.util.Locale;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Query;

/**
 * Traduce la sintassi semplice richiesta dalla traccia in una Query di Lucene.
 *
 * Sintassi:  &lt;campo&gt; &lt;termini&gt;
 *   campo   = nome | contenuto
 *   termini = una o piu' parole; tra virgolette diventano una phrase query.
 *             Sono ammessi anche gli operatori del QueryParser: +obbligatorio, -escluso.
 *
 * Esempi:
 *   contenuto cavallo
 *   contenuto "nel mezzo del cammin"
 *   contenuto +guerra +pace
 *   nome commedia
 *
 * Usata sia dal programma di Ricerca sia dalla Valutazione, cosi' le due cose
 * interpretano le query esattamente nello stesso modo.
 */
public final class InterpreteQuery {

    private InterpreteQuery() {
    }

    public static Query interpreta(String riga, Analyzer analyzer) throws ParseException {
        String testo = riga == null ? "" : riga.trim();
        String[] parti = testo.split("\\s+", 2);
        String parolaChiave = parti[0].toLowerCase(Locale.ROOT);

        String campo;
        if (parolaChiave.equals("nome")) {
            campo = Campi.NOME;
        } else if (parolaChiave.equals("contenuto")) {
            campo = Campi.CONTENUTO;
        } else {
            throw new IllegalArgumentException("La query deve iniziare con 'nome' o 'contenuto'.");
        }
        if (parti.length < 2 || parti[1].isBlank()) {
            throw new IllegalArgumentException("Dopo '" + parolaChiave + "' scrivi almeno un termine.");
        }

        // Il QueryParser analizza i termini con l'analyzer del campo (grazie al
        // PerFieldAnalyzerWrapper): la query subisce la stessa analisi dell'indice.
        QueryParser parser = new QueryParser(campo, analyzer);
        // Piu' termini senza operatori = OR (e' il default; lo rendiamo esplicito).
        parser.setDefaultOperator(QueryParser.Operator.OR);
        Query query = parser.parse(parti[1]);
        if (query == null) {
            throw new IllegalArgumentException(
                    "Dopo l'analisi la query e' vuota (forse contiene solo stopword).");
        }
        return query;
    }
}
