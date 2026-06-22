'use client';
import { useEffect, useState, useMemo, useCallback } from 'react';
import { fetchCase, fetchQueries } from '@/lib/api';
import {
  MergedCase, MergedField, CaseClassification, ReviewQuery,
  SECTION_LABELS, confidenceLabel
} from '@/types';
import FieldCard from '@/components/FieldCard';
import QueryModal from '@/components/QueryModal';
import QueryList from '@/components/QueryList';

// Fallback: if backend is down, import directly
// import fallbackCase from '@/data/case_v1_fallback.json';

type SortMode = 'default' | 'confidence-asc';
type FilterMode = 'all' | 'conflicts';
type ViewMode = 'fields' | 'queries';

const CASE_ID = 'PV-2026-0451';

export default function CaseViewerPage() {
  const [caseData, setCaseData] = useState<MergedCase | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [classification, setClassification] = useState<CaseClassification>(null);
  const [sortMode, setSortMode] = useState<SortMode>('default');
  const [filterMode, setFilterMode] = useState<FilterMode>('all');
  const [viewMode, setViewMode] = useState<ViewMode>('fields');
  const [queries, setQueries] = useState<ReviewQuery[]>([]);
  const [queriesLoading, setQueriesLoading] = useState(false);
  const [queryModal, setQueryModal] = useState<{ open: boolean; fieldPath: string }>({
    open: false, fieldPath: '',
  });

  const loadQueries = useCallback(() => {
    setQueriesLoading(true);
    fetchQueries(CASE_ID)
      .then(setQueries)
      .catch(() => {})
      .finally(() => setQueriesLoading(false));
  }, []);

  useEffect(() => {
    fetchCase(CASE_ID)
      .then((data: MergedCase) => {
        setCaseData(data);
        setClassification(data.case_classification ?? null);
      })
      .catch(e => setError(e.message))
      .finally(() => setLoading(false));
    loadQueries();
  }, [loadQueries]);

  // Flatten all fields for sort/filter
  const allFields = useMemo(() => {
    if (!caseData) return [];
    const flat: Array<{ section: string; field: string; data: MergedField; path: string }> = [];
    Object.entries(caseData.sections).forEach(([section, fields]) => {
      Object.entries(fields).forEach(([field, data]) => {
        flat.push({ section, field, data, path: `sections.${section}.${field}` });
      });
    });
    return flat;
  }, [caseData]);

  const filteredFields = useMemo(() => {
    let list = allFields;
    if (filterMode === 'conflicts') list = list.filter(f => f.data.status === 'overridden');
    if (sortMode === 'confidence-asc') list = [...list].sort((a, b) => a.data.confidence - b.data.confidence);
    return list;
  }, [allFields, filterMode, sortMode]);

  // Group back into sections after filter/sort
  const groupedSections = useMemo(() => {
    const map = new Map<string, typeof filteredFields>();
    filteredFields.forEach(f => {
      if (!map.has(f.section)) map.set(f.section, []);
      map.get(f.section)!.push(f);
    });
    return map;
  }, [filteredFields]);

  const openQueryModal = (fieldPath: string) =>
    setQueryModal({ open: true, fieldPath });

  // ── Render states ─────────────────────────────────────────────────────────

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="text-brand text-sm font-medium animate-pulse">Loading case…</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50 flex items-center justify-center">
        <div className="bg-white border border-red-200 rounded-xl p-8 max-w-md text-center">
          <p className="text-red-600 font-medium mb-2">Failed to load case</p>
          <p className="text-gray-500 text-sm">{error}</p>
          <p className="text-gray-400 text-xs mt-4">
            Is the backend running on port 8080? Run:{' '}
            <code className="font-mono bg-gray-100 px-1 rounded">./ops/run.sh start</code>
          </p>
        </div>
      </div>
    );
  }

  if (!caseData) return null;

  const conflictCount = allFields.filter(f => f.data.status === 'overridden').length;
  const newCount = allFields.filter(f => f.data.status === 'new').length;

  return (
    <div className="min-h-screen bg-gray-50">
      {/* ── Top bar ─────────────────────────────────────────────────────── */}
      <header className="bg-navy text-white px-6 py-4 flex items-center justify-between shadow-lg">
        <div>
          <h1 className="text-lg font-bold tracking-tight">PV Case Review</h1>
          <p className="text-teal text-xs font-mono mt-0.5">{caseData.case_id} · v{caseData.version}</p>
        </div>
        {/* Classification selector */}
        <div className="flex items-center gap-3">
          <span className="text-xs text-gray-400">Classification</span>
          <select
            value={classification ?? ''}
            onChange={e => setClassification((e.target.value as CaseClassification) || null)}
            className="bg-white/10 text-white border border-white/20 rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:ring-2 focus:ring-teal"
          >
            <option value="">— not set —</option>
            <option value="significant">Significant</option>
            <option value="non-significant">Non-Significant</option>
          </select>
        </div>
      </header>

      {/* ── Summary bar ─────────────────────────────────────────────────── */}
      <div className="bg-white border-b border-gray-200 px-6 py-3 flex items-center gap-6 text-sm">
        <span className="text-gray-500">
          Source: <span className="font-mono text-gray-700">{caseData.source_document}</span>
        </span>
        {conflictCount > 0 && (
          <span className="font-medium text-amber-700">{conflictCount} conflict{conflictCount > 1 ? 's' : ''}</span>
        )}
        {newCount > 0 && (
          <span className="font-medium text-teal">{newCount} new field{newCount > 1 ? 's' : ''}</span>
        )}
        {/* Missing fields warning */}
        {caseData.missing_fields?.length > 0 && (
          <div className="ml-auto flex items-center gap-2 bg-red-50 border border-red-200 text-red-700 text-xs px-3 py-1 rounded-full">
            <span>⚠ Missing: {caseData.missing_fields.join(', ')}</span>
          </div>
        )}
      </div>

      {/* ── Controls ────────────────────────────────────────────────────── */}
      <div className="px-6 py-3 flex items-center gap-4 border-b border-gray-200 bg-white">
        {/* View toggle */}
        <div className="flex gap-2">
          <button
            onClick={() => setViewMode('fields')}
            className={`px-3 py-1 rounded-full text-sm font-medium transition-colors ${viewMode === 'fields' ? 'bg-brand text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
          >
            Fields
          </button>
          <button
            onClick={() => setViewMode('queries')}
            className={`px-3 py-1 rounded-full text-sm font-medium transition-colors ${viewMode === 'queries' ? 'bg-brand text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
          >
            Queries {queries.length > 0 && <span className="ml-1 bg-white/30 text-inherit rounded-full px-1.5 text-[11px]">{queries.length}</span>}
          </button>
        </div>

        {/* Field filter + sort — only shown in fields view */}
        {viewMode === 'fields' && (
          <>
            <div className="flex gap-2">
              <button
                onClick={() => setFilterMode('all')}
                className={`px-3 py-1 rounded-full text-sm font-medium transition-colors ${filterMode === 'all' ? 'bg-navy text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
              >
                All fields
              </button>
              <button
                onClick={() => setFilterMode('conflicts')}
                className={`px-3 py-1 rounded-full text-sm font-medium transition-colors ${filterMode === 'conflicts' ? 'bg-amber-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
              >
                Conflicts only
              </button>
            </div>
            <div className="ml-auto flex items-center gap-2 text-sm text-gray-600">
              <span>Sort:</span>
              <button
                onClick={() => setSortMode(s => s === 'confidence-asc' ? 'default' : 'confidence-asc')}
                className={`px-3 py-1 rounded-full font-medium transition-colors ${sortMode === 'confidence-asc' ? 'bg-navy text-white' : 'bg-gray-100 hover:bg-gray-200'}`}
              >
                Confidence ↑
              </button>
            </div>
          </>
        )}
      </div>

      {/* ── Main content ────────────────────────────────────────────────── */}
      <main className="max-w-5xl mx-auto px-6 py-6 space-y-8">
        {viewMode === 'queries' ? (
          <QueryList queries={queries} loading={queriesLoading} />
        ) : groupedSections.size === 0 ? (
          <div className="text-center text-gray-400 py-16">No fields match the current filter.</div>
        ) : (
          Array.from(groupedSections.entries()).map(([section, fields]) => (
            <section key={section}>
              <h2 className="text-sm font-bold uppercase tracking-widest text-navy mb-3 flex items-center gap-2">
                <span className="w-1 h-4 rounded bg-brand inline-block" />
                {SECTION_LABELS[section] ?? section}
              </h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                {fields.map(({ field, data, path }) => (
                  <FieldCard
                    key={path}
                    label={field.replace(/_/g, ' ')}
                    field={data}
                    fieldPath={path}
                    onRaiseQuery={openQueryModal}
                  />
                ))}
              </div>
            </section>
          ))
        )}
      </main>

      {/* ── Query Modal ─────────────────────────────────────────────────── */}
      {queryModal.open && (
        <QueryModal
          caseId={caseData.case_id}
          fieldPath={queryModal.fieldPath}
          onClose={() => setQueryModal({ open: false, fieldPath: '' })}
          onSuccess={loadQueries}
        />
      )}
    </div>
  );
}
