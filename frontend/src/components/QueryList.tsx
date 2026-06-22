'use client';
import { ReviewQuery } from '@/types';

interface Props {
  queries: ReviewQuery[];
  loading: boolean;
}

export default function QueryList({ queries, loading }: Props) {
  if (loading) {
    return (
      <div className="flex items-center justify-center py-16">
        <p className="text-brand text-sm font-medium animate-pulse">Loading queries…</p>
      </div>
    );
  }

  if (queries.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center py-16 text-gray-400">
        <p className="text-sm">No queries raised yet.</p>
        <p className="text-xs mt-1">Click "Raise Query" on a conflicted field to add one.</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {queries.map(q => (
        <div key={q.id} className="bg-white border border-gray-200 rounded-lg p-4">
          <div className="flex items-start justify-between gap-3 mb-2">
            <span className="text-xs font-mono text-gray-500 break-all">{q.field_path}</span>
            <span className={`shrink-0 text-[11px] font-medium px-2 py-0.5 rounded-full ${
              q.status === 'open'
                ? 'bg-amber-100 text-amber-800 border border-amber-300'
                : 'bg-gray-100 text-gray-500 border border-gray-200'
            }`}>
              {q.status}
            </span>
          </div>
          <p className="text-sm text-gray-900">{q.question}</p>
          <p className="text-[11px] text-gray-400 mt-3">
            {new Date(q.created_at).toLocaleString()}
          </p>
        </div>
      ))}
    </div>
  );
}
