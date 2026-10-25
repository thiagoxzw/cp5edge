import { scoreLabel, scoreTone } from '../lib/status'

/** Medidor circular do Wine Score (0–100). */
export function WineScoreGauge({ score, size = 132, label }: { score: number | null | undefined; size?: number; label?: string | null }) {
  const stroke = Math.max(6, size / 14)
  const r = (size - stroke) / 2
  const c = 2 * Math.PI * r
  const arc = 0.75 // 270°
  const value = score ?? 0
  const tone = scoreTone(score)
  return (
    <div className="relative inline-flex items-center justify-center" style={{ width: size, height: size }}>
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} className="rotate-[135deg]" aria-hidden>
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--color-line-strong)" strokeWidth={stroke}
          strokeDasharray={`${c * arc} ${c}`} strokeLinecap="round" />
        <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke={tone.stroke} strokeWidth={stroke}
          strokeDasharray={`${(c * arc * value) / 100} ${c}`} strokeLinecap="round"
          style={{ transition: 'stroke-dasharray 0.8s ease, stroke 0.4s' }} />
      </svg>
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <span className="tabular text-[length:var(--s)] font-semibold leading-none tracking-tight" style={{ ['--s' as string]: `${size / 3.6}px` }}>
          {score ?? '—'}
        </span>
        <span className={`mt-1 text-[11px] font-medium ${tone.text}`}>{label ?? scoreLabel(score)}</span>
      </div>
    </div>
  )
}

/** Versão compacta para cards. */
export function ScorePill({ score }: { score: number | null | undefined }) {
  const tone = scoreTone(score)
  return (
    <span className="inline-flex items-baseline gap-1">
      <span className={`tabular text-lg font-semibold ${tone.text}`}>{score ?? '—'}</span>
      <span className="text-[10px] text-subtle">/100</span>
    </span>
  )
}
