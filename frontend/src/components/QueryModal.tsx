'use client';
import { useRef, useState } from 'react';
import { postQuery } from '@/lib/api';

interface Props {
  caseId: string;
  fieldPath: string;
  onClose: () => void;
  onSuccess?: () => void;
}

export default function QueryModal({ caseId, fieldPath, onClose, onSuccess }: Props) {
  const [question, setQuestion] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleSubmit = async () => {
    if (!question.trim()) {
      setError('Question cannot be empty.');
      return;
    }
    setSubmitting(true);
    setError('');
    try {
      await postQuery(caseId, fieldPath, question.trim());
      onSuccess?.();
      onClose();
    } catch (e: any) {
      setError(e.message ?? 'Failed to submit query.');
    } finally {
      setSubmitting(false);
    }
  };

  // Close on backdrop click
  const handleBackdrop = (e: React.MouseEvent<HTMLDivElement>) => {
    if (e.target === e.currentTarget) onClose();
  };

  return (
    <div
      className="fixed inset-0 bg-navy/50 flex items-center justify-center z-50"
      onClick={handleBackdrop}
    >
      <div className="bg-white rounded-xl shadow-2xl w-full max-w-md mx-4 p-6">
        <h2 className="text-lg font-semibold text-navy mb-1">Raise Query</h2>
        <p className="text-xs text-gray-500 font-mono mb-4">{fieldPath}</p>

        <textarea
          ref={textareaRef}
          autoFocus
          value={question}
          onChange={e => setQuestion(e.target.value)}
          placeholder="Describe the issue or question for the source team…"
          rows={4}
          className="w-full border border-gray-300 rounded-lg p-3 text-sm resize-none focus:outline-none focus:ring-2 focus:ring-brand"
        />

        {error && <p className="text-red-500 text-xs mt-1">{error}</p>}

        <div className="flex justify-end gap-3 mt-4">
          <button
            onClick={onClose}
            className="px-4 py-2 text-sm text-gray-600 hover:text-navy transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleSubmit}
            disabled={submitting}
            className="px-4 py-2 text-sm font-medium bg-brand text-white rounded-lg hover:bg-navy transition-colors disabled:opacity-50"
          >
            {submitting ? 'Submitting…' : 'Submit Query'}
          </button>
        </div>
      </div>
    </div>
  );
}
