'use client';
import { MergedField, confidenceLabel } from '@/types';
import { useState } from 'react';

interface Props {
  label: string;
  field: MergedField;
  fieldPath: string;
  onRaiseQuery: (fieldPath: string) => void;
}

const STATUS_PILL: Record<string, string> = {
  overridden: 'bg-amber-100 text-amber-800 border border-amber-300',
  new:        'bg-teal/10 text-teal border border-teal',
  unchanged:  'bg-gray-100 text-gray-500 border border-gray-200',
};

const CONF_COLOUR: Record<string, string> = {
  high:   'text-conf-high',
  medium: 'text-conf-medium',
  low:    'text-conf-low',
};

export default function FieldCard({ label, field, fieldPath, onRaiseQuery }: Props) {
  const confLevel = confidenceLabel(field.confidence);
  const isConflict = field.status === 'overridden';

  return (
    <div className={`rounded-lg border p-4 ${isConflict ? 'border-amber-300 bg-amber-50' : 'border-gray-200 bg-white'}`}>
      {/* Header row */}
      <div className="flex items-center justify-between mb-2">
        <span className="text-xs font-semibold uppercase tracking-wide text-gray-500">{label}</span>
        <div className="flex items-center gap-2">
          {/* Status pill */}
          <span className={`text-[11px] font-medium px-2 py-0.5 rounded-full ${STATUS_PILL[field.status] ?? STATUS_PILL.unchanged}`}>
            {field.status}
          </span>
          {/* Confidence badge */}
          <span className={`text-xs font-mono font-medium ${CONF_COLOUR[confLevel]}`}>
            {(field.confidence * 100).toFixed(0)}%
          </span>
        </div>
      </div>

      {/* Value — conflict shows both */}
      {isConflict ? (
        <div className="flex items-start gap-3">
          <div className="flex-1">
            <p className="text-[11px] text-gray-400 mb-0.5">New value</p>
            <p className="font-semibold text-gray-900">{field.value}</p>
          </div>
          <div className="w-px bg-amber-200 self-stretch" />
          <div className="flex-1">
            <p className="text-[11px] text-gray-400 mb-0.5">Previous value</p>
            <p className="text-gray-400 line-through">{field.previous_value}</p>
          </div>
        </div>
      ) : (
        <p className="font-semibold text-gray-900">{field.value}</p>
      )}

      {/* Source + query button */}
      <div className="flex items-center justify-between mt-3">
        <span className="text-[11px] text-gray-400 font-mono">{field.source}</span>
        {isConflict && (
          <button
            onClick={() => onRaiseQuery(fieldPath)}
            className="text-[11px] font-medium text-brand hover:text-navy underline underline-offset-2 transition-colors"
          >
            Raise Query
          </button>
        )}
      </div>
    </div>
  );
}
