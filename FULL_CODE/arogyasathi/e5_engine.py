"""Multilingual E5 Small ONNX INT8 Retrieval Engine for Arogyasathi.

Implements edge dense embedding generation and hybrid retrieval over the 107 leaf protocols:
1. Model: intfloat/multilingual-e5-small quantized to INT8 (~112.6 MB).
2. Supports 100+ languages including Hindi, Telugu, Tamil, Kannada, Malayalam, Marathi, Bengali, English.
3. Pre-computes and caches protocol embeddings (107 sections x 384 dims = 164 KB).
4. Hybrid search: Combines Dense Cosine Similarity with BM25 for maximum semantic and lexical precision.
"""

from __future__ import annotations
import os
import time
from typing import List, Tuple, Dict, Any, Optional
import numpy as np

try:
    import onnxruntime as ort
    from tokenizers import Tokenizer
    HAS_ONNX = True
except ImportError:
    HAS_ONNX = False

from .protocols import load_protocol_sections
from .laya import LayaRetriever


DEFAULT_MODEL_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), "models", "multilingual-e5-small-int8")


class E5OnnxEmbedder:
    """CPU-optimized ONNX INT8 embedder for multilingual-e5-small."""

    def __init__(self, model_dir: Optional[str] = None):
        if not HAS_ONNX:
            raise ImportError("onnxruntime and tokenizers are required for E5OnnxEmbedder.")

        self.model_dir = model_dir or DEFAULT_MODEL_DIR
        self.model_path = os.path.join(self.model_dir, "model_int8.onnx")
        self.tokenizer_path = os.path.join(self.model_dir, "tokenizer.json")

        if not os.path.exists(self.model_path):
            raise FileNotFoundError(f"ONNX model not found at {self.model_path}")
        if not os.path.exists(self.tokenizer_path):
            raise FileNotFoundError(f"Tokenizer not found at {self.tokenizer_path}")

        # Initialize fast Rust-based Tokenizer
        self.tokenizer = Tokenizer.from_file(self.tokenizer_path)
        self.tokenizer.enable_truncation(max_length=512)
        self.tokenizer.enable_padding(length=512)

        # Initialize ONNX Session with optimized CPU execution
        opts = ort.SessionOptions()
        opts.intra_op_num_threads = min(4, os.cpu_count() or 2)
        opts.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL

        self.session = ort.InferenceSession(self.model_path, sess_options=opts, providers=["CPUExecutionProvider"])
        self.input_names = [inp.name for inp in self.session.get_inputs()]
        self.output_names = [out.name for out in self.session.get_outputs()]

    def encode(self, texts: List[str], is_query: bool = True, batch_size: int = 16) -> np.ndarray:
        """Encodes texts into normalized 384-dimensional dense embeddings.

        e5 requires:
          - 'query: ' prefix for search queries
          - 'passage: ' prefix for corpus documents/passages
        """
        prefix = "query: " if is_query else "passage: "
        prefixed_texts = [f"{prefix}{t.strip()}" for t in texts]

        all_embeddings = []

        for i in range(0, len(prefixed_texts), batch_size):
            batch = prefixed_texts[i : i + batch_size]
            encodings = self.tokenizer.encode_batch(batch)

            input_ids = np.array([e.ids for e in encodings], dtype=np.int64)
            attention_mask = np.array([e.attention_mask for e in encodings], dtype=np.int64)

            feed = {
                "input_ids": input_ids,
                "attention_mask": attention_mask
            }
            if "token_type_ids" in self.input_names:
                feed["token_type_ids"] = np.zeros_like(input_ids)

            res = self.session.run(None, feed)
            token_embeddings = res[0]  # Shape: [batch, seq_len, 384]

            # Mean pooling with attention mask
            mask_expanded = np.expand_dims(attention_mask, -1).astype(np.float32)
            sum_embeddings = np.sum(token_embeddings * mask_expanded, axis=1)
            sum_mask = np.clip(np.sum(mask_expanded, axis=1), 1e-9, None)
            mean_pooled = sum_embeddings / sum_mask

            # L2 Normalization (unit vectors for cosine similarity via dot product)
            norm = np.linalg.norm(mean_pooled, axis=1, keepdims=True)
            normalized = mean_pooled / np.clip(norm, 1e-9, None)
            all_embeddings.append(normalized)

        return np.vstack(all_embeddings).astype(np.float32)


class HybridE5Retriever:
    """Hybrid Protocol Retriever combining Multilingual E5 Dense Embeddings with BM25 Lexical Ranking."""

    def __init__(
        self,
        sections: Dict[str, Dict[str, Any]],
        embedder: Optional[E5OnnxEmbedder] = None,
        cache_path: Optional[str] = None
    ):
        self.sections = sections
        self.doc_ids = list(sections.keys())
        self.bm25_retriever = LayaRetriever(sections)
        self.embedder = embedder

        self.cache_path = cache_path or os.path.join(
            DEFAULT_MODEL_DIR, "protocol_embeddings_int8.npy"
        )
        self.doc_embeddings: Optional[np.ndarray] = None

        if self.embedder is not None:
            self._init_embeddings()

    def _init_embeddings(self) -> None:
        """Loads cached document embeddings or computes them once."""
        if os.path.exists(self.cache_path):
            try:
                cached = np.load(self.cache_path, allow_pickle=True).item()
                if cached.get("doc_ids") == self.doc_ids:
                    self.doc_embeddings = cached["embeddings"]
                    return
            except Exception:
                pass

        # Compute document embeddings (107 sections)
        corpus_texts = []
        for sid in self.doc_ids:
            sec = self.sections[sid]
            # Include Section ID, Title, and Clean Body Text
            clean_text = sec["text"].replace("\n", " ")
            text = f"{sid}: {sec['title']}. {clean_text}"
            corpus_texts.append(text)

        self.doc_embeddings = self.embedder.encode(corpus_texts, is_query=False, batch_size=16)

        # Cache to disk for instant zero-latency future loading
        try:
            os.makedirs(os.path.dirname(self.cache_path), exist_ok=True)
            np.save(self.cache_path, {"doc_ids": self.doc_ids, "embeddings": self.doc_embeddings})
        except Exception:
            pass

    def retrieve(
        self,
        query: str,
        top_k: int = 3,
        dense_weight: float = 0.65,
        bm25_weight: float = 0.35
    ) -> List[Tuple[float, str]]:
        """Performs hybrid retrieval combining dense semantic similarity and lexical BM25 matching."""
        # 1. Lexical BM25 Ranking
        raw_bm25 = self.bm25_retriever.retrieve(query, top_k=len(self.doc_ids))
        bm25_hits = {sid: sc for sc, sid in raw_bm25}
        max_bm25 = max(bm25_hits.values()) if bm25_hits else 1.0

        # Normalize BM25 scores to [0, 1]
        norm_bm25 = {k: (v / max_bm25) if max_bm25 > 0 else 0.0 for k, v in bm25_hits.items()}

        # 2. Dense Semantic Search (if ONNX embedder available)
        norm_dense: Dict[str, float] = {}
        if self.embedder is not None and self.doc_embeddings is not None:
            q_emb = self.embedder.encode([query], is_query=True)[0]  # [384]
            # Cosine similarity via dot product (embeddings are already L2 normalized)
            dense_scores = np.dot(self.doc_embeddings, q_emb)  # [107]
            for i, sid in enumerate(self.doc_ids):
                # Scale cosine similarity [-1, 1] to [0, 1]
                norm_dense[sid] = float(np.clip((dense_scores[i] + 1.0) / 2.0, 0.0, 1.0))
        else:
            dense_weight = 0.0
            bm25_weight = 1.0

        # 3. Fuse scores
        combined_scores = []
        for sid in self.doc_ids:
            d_score = norm_dense.get(sid, 0.0)
            b_score = norm_bm25.get(sid, 0.0)
            final_score = (dense_weight * d_score) + (bm25_weight * b_score)
            combined_scores.append((final_score, sid))

        combined_scores.sort(reverse=True)
        return combined_scores[:top_k]
