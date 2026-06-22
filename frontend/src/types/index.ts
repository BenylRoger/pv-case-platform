// types/index.ts — mirrors the Java MergedCase response shape

export type FieldStatus = 'unchanged' | 'overridden' | 'new';

export interface MergedField {
  value: string;
  confidence: number;
  source: string;
  status: FieldStatus;
  previous_value?: string;
}

export interface MergedCase {
  case_id: string;
  version: number;
  case_classification: 'significant' | 'non-significant' | null;
  merged_at: string;
  source_document: string;
  missing_fields: string[];
  sections: Record<string, Record<string, MergedField>>;
}

export interface ReviewQuery {
  id: string;
  case_id: string;
  field_path: string;
  question: string;
  created_at: string;
  status: string;
}

export type CaseClassification = 'significant' | 'non-significant' | null;

// Confidence thresholds per brief
export const CONF_HIGH = 0.90;
export const CONF_LOW  = 0.80;

export function confidenceLabel(score: number): 'high' | 'medium' | 'low' {
  if (score > CONF_HIGH) return 'high';
  if (score >= CONF_LOW) return 'medium';
  return 'low';
}

export const SECTION_LABELS: Record<string, string> = {
  patient:       'Patient',
  suspect_drug:  'Suspect Drug',
  adverse_event: 'Adverse Event',
  reporter:      'Reporter',
};
