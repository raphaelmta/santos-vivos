package br.santosvivos.corpus;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.fr.FrenchAnalyzer;
import org.apache.lucene.analysis.pt.PortugueseAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

import java.io.IOException;
import java.util.*;

/** Índice BM25 em memória (Lucene) sobre os trechos de um santo. Analyzer conforme o idioma do corpus. */
public class IndiceLucene {
    private final Directory dir = new ByteBuffersDirectory();
    private final Analyzer analyzer;
    private final List<Trecho> trechos;
    private IndexSearcher searcher;

    public IndiceLucene(List<Trecho> trechos, String idioma) throws IOException {
        this.trechos = trechos;
        this.analyzer = "pt".equals(idioma) ? new PortugueseAnalyzer() : new FrenchAnalyzer();
        try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
            for (int i = 0; i < trechos.size(); i++) {
                Trecho t = trechos.get(i);
                Document d = new Document();
                d.add(new StoredField("pos", i));
                d.add(new TextField("texto", t.texto(), Field.Store.NO));
                d.add(new TextField("titulo", t.titulo() == null ? "" : t.titulo(), Field.Store.NO));
                w.addDocument(d);
            }
        }
        searcher = new IndexSearcher(DirectoryReader.open(dir));
    }

    public int tamanho() { return trechos.size(); }

    public List<Trecho> buscar(String consulta, int k) throws IOException {
        String q = QueryParser.escape(consulta).strip();
        if (q.isEmpty()) return List.of();
        QueryParser texto = new QueryParser("texto", analyzer);
        QueryParser titulo = new QueryParser("titulo", analyzer);
        Query query;
        try {
            query = new BooleanQuery.Builder()
                    .add(texto.parse(q), BooleanClause.Occur.SHOULD)
                    .add(new BoostQuery(titulo.parse(q), 1.5f), BooleanClause.Occur.SHOULD).build();
        } catch (Exception e) { return List.of(); }
        TopDocs top = searcher.search(query, k);
        List<Trecho> out = new ArrayList<>();
        StoredFields sf = searcher.storedFields();
        for (ScoreDoc sd : top.scoreDocs) out.add(trechos.get(sf.document(sd.doc).getField("pos").numericValue().intValue()));
        return out;
    }
}
