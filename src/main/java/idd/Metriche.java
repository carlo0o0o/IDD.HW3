package idd;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Metriche di valutazione per UNA query, dati:
 *   - la classifica restituita dal sistema (nomi dei file, in ordine di score);
 *   - l'insieme dei documenti rilevanti (ground truth).
 *
 * precision = rilevanti recuperati / recuperati
 * recall    = rilevanti recuperati / rilevanti
 * F1        = 2PR / (P + R)
 * P@10      = rilevanti tra i primi 10 / 10
 * AP        = media delle precision misurate alla posizione di ciascun rilevante
 *             recuperato, divisa per il numero totale di rilevanti (la media sulle
 *             query e' la MAP)
 *
 * I valori non definiti (es. recall di una query senza documenti rilevanti) sono
 * NaN, vengono stampati come "-" ed esclusi dalle medie.
 */
public final class Metriche {

    public final int rilevanti;
    public final int recuperati;
    public final int rilevantiRecuperati;
    public final double precision;
    public final double recall;
    public final double f1;
    public final double pAt10;
    public final double averagePrecision;

    private Metriche(int rilevanti, int recuperati, int rilevantiRecuperati,
                     double precision, double recall, double f1, double pAt10, double ap) {
        this.rilevanti = rilevanti;
        this.recuperati = recuperati;
        this.rilevantiRecuperati = rilevantiRecuperati;
        this.precision = precision;
        this.recall = recall;
        this.f1 = f1;
        this.pAt10 = pAt10;
        this.averagePrecision = ap;
    }

    public static Metriche calcola(List<String> classifica, Set<String> rilevanti) {
        int trovati = 0;
        int nei10 = 0;
        double sommaPrecision = 0.0;
        for (int i = 0; i < classifica.size(); i++) {
            if (rilevanti.contains(classifica.get(i))) {
                trovati++;
                sommaPrecision += (double) trovati / (i + 1);
                if (i < 10) {
                    nei10++;
                }
            }
        }
        int nRec = classifica.size();
        int nRil = rilevanti.size();
        // Nessun documento recuperato: precision 0 se esistevano rilevanti (convenzione
        // usata anche da trec_eval), non definita solo se non c'era nulla da trovare.
        double p = nRec == 0 ? (nRil == 0 ? Double.NaN : 0.0) : (double) trovati / nRec;
        double r = nRil == 0 ? Double.NaN : (double) trovati / nRil;
        double f;
        if (Double.isNaN(p) || Double.isNaN(r)) {
            f = Double.NaN;
        } else if (p + r == 0) {
            f = 0.0;
        } else {
            f = 2 * p * r / (p + r);
        }
        double p10 = nei10 / 10.0;
        double ap = nRil == 0 ? Double.NaN : sommaPrecision / nRil;
        return new Metriche(nRil, nRec, trovati, p, r, f, p10, ap);
    }

    /** Formatta con 3 decimali e il punto; "-" se il valore non e' definito. */
    public static String fmt(double v) {
        return Double.isNaN(v) ? "-" : String.format(Locale.ROOT, "%.3f", v);
    }
}
