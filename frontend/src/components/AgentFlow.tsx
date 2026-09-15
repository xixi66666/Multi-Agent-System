import { useGSAP } from '@gsap/react'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import {
  ArrowDownRight,
  Blocks,
  Bot,
  BrainCircuit,
  CheckCircle2,
  FlaskConical,
  GitMerge,
  SearchCode,
  ShieldCheck,
  Sparkles,
} from 'lucide-react'
import { useMemo, useRef } from 'react'
import { agentRoleMeta, AUTONOMOUS_ROLE_ORDER, formatSpecialty } from '../agentRoles'
import { formatTime } from '../format'
import type { AgentTask } from '../types'
import { StatusBadge } from './StatusBadge'

gsap.registerPlugin(useGSAP, ScrollTrigger)

function roleIcon(role: string) {
  const props = { size: 18, 'aria-hidden': true as const }
  if (role === 'PLANNER') return <BrainCircuit {...props} />
  if (role === 'ARCHITECT') return <Blocks {...props} />
  if (role === 'IMPLEMENTER') return <Bot {...props} />
  if (role === 'TESTER') return <FlaskConical {...props} />
  if (role === 'REVIEWER') return <ShieldCheck {...props} />
  if (role === 'INTEGRATOR') return <GitMerge {...props} />
  if (role === 'RESEARCHER') return <SearchCode {...props} />
  return <CheckCircle2 {...props} />
}

export function AgentFlow({ tasks }: { tasks: AgentTask[] }) {
  const scope = useRef<HTMLElement>(null)
  const presentRoles = useMemo(() => new Set(tasks.map((task) => task.role)), [tasks])
  const visibleRoles = useMemo(() => {
    const unique = [...presentRoles]
    return unique.length > 0 ? unique : [...AUTONOMOUS_ROLE_ORDER]
  }, [presentRoles])
  const completedCount = tasks.filter((task) => task.status === 'COMPLETED').length
  const runningCount = tasks.filter((task) => task.status === 'RUNNING').length
  const story = '从需求拆解到最终审查，每个智能体只负责自己擅长的一段工作，并把可验证的结果交给下一位协作者。'

  useGSAP(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return

    gsap.from('.flow-hero-copy > *', {
      y: 20,
      opacity: 0,
      duration: 0.72,
      stagger: 0.08,
      ease: 'power3.out',
    })
    gsap.from('.role-accordion', {
      y: 18,
      opacity: 0,
      duration: 0.58,
      stagger: 0.06,
      delay: 0.14,
      ease: 'power2.out',
    })

    const words = gsap.utils.toArray<HTMLElement>('.flow-story-word')
    gsap.fromTo(words, { opacity: 0.2 }, {
      opacity: 1,
      stagger: 0.035,
      ease: 'none',
      scrollTrigger: {
        trigger: '.flow-story',
        start: 'top 88%',
        end: 'bottom 58%',
        scrub: 0.45,
      },
    })

    const cards = gsap.utils.toArray<HTMLElement>('.agent-task-card')
    cards.forEach((card, index) => {
      gsap.from(card, {
        y: 34 + Math.min(index * 4, 20),
        scale: 0.965,
        opacity: 0,
        duration: 0.72,
        ease: 'power3.out',
        scrollTrigger: {
          trigger: card,
          start: 'top 92%',
          once: true,
        },
      })
    })
  }, { scope, dependencies: [tasks.length] })

  return (
    <section className="flow-panel flow-panel-premium" aria-label="智能体执行流程" ref={scope}>
      <div className="flow-hero">
        <div className="flow-hero-copy">
          <div className="flow-eyebrow">
            <span className={runningCount > 0 ? 'is-running' : ''} />
            {runningCount > 0 ? `${runningCount} 位智能体正在工作` : '智能体协作图谱'}
          </div>
          <h2>
            看懂每个
            <span className="flow-inline-image" aria-hidden="true">
              <BrainCircuit size={19} />
              <ArrowDownRight size={15} />
            </span>
            智能体，跟上每一步交付
          </h2>
          <p className="flow-story">
            {story.split('').map((character, index) => (
              <span className="flow-story-word" key={`${character}-${index}`}>{character}</span>
            ))}
          </p>
        </div>
        <div className="flow-hero-stats" aria-label="任务流概览">
          <div><strong>{visibleRoles.length}</strong><span>类协作角色</span></div>
          <div><strong>{tasks.length}</strong><span>个执行任务</span></div>
          <div><strong>{completedCount}</strong><span>项已经完成</span></div>
        </div>
      </div>

      <div className={`role-directory ${visibleRoles.length <= 3 ? 'is-sparse' : ''}`} aria-label="本次协作角色及其职责">
        {visibleRoles.map((role) => {
          const meta = agentRoleMeta(role)
          return (
            <article className={`role-accordion role-tone-${meta.tone}`} key={role} tabIndex={0}>
              <div className="role-accordion-icon">{roleIcon(role)}</div>
              <div className="role-accordion-copy">
                <span>{meta.phase}</span>
                <strong>{meta.label}</strong>
                <p>{meta.description}</p>
              </div>
              <small>{meta.deliverable}</small>
            </article>
          )
        })}
      </div>

      <div className="delivery-marquee" aria-label="标准交付链路">
        <div className="delivery-marquee-track">
          {[...AUTONOMOUS_ROLE_ORDER, ...AUTONOMOUS_ROLE_ORDER].map((role, index) => {
            const meta = agentRoleMeta(role)
            return (
              <span className={presentRoles.has(role) ? 'is-present' : ''} key={`${role}-${index}`} aria-hidden={index >= AUTONOMOUS_ROLE_ORDER.length}>
                <i /> {meta.phase} <small>{meta.shortLabel}</small>
              </span>
            )
          })}
        </div>
      </div>

      <div className="agent-flow-heading">
        <div>
          <Sparkles size={18} aria-hidden="true" />
          <h3>任务正在如何推进</h3>
        </div>
        <p>按调度顺序排列，卡片会持续更新当前结果。</p>
      </div>

      <div className="agent-flow agent-flow-list">
        {tasks.map((task, index) => (
          <article className={`agent-row agent-task-card role-tone-${agentRoleMeta(task.role).tone}`} key={task.id}>
            <div className="flow-rail" aria-hidden="true">
              <span className="flow-order">{String(index + 1).padStart(2, '0')}</span>
              <span className={`flow-node ${task.status.toLowerCase()}`}>{roleIcon(task.role)}</span>
              {index < tasks.length - 1 && <span className="flow-line" />}
            </div>
            <div className="agent-row-content">
              <div className="agent-row-topline">
                <div className="agent-identity">
                  <span className="role-label">{agentRoleMeta(task.role).label}</span>
                  <strong>{agentRoleMeta(task.role).phase}</strong>
                  <small>{formatSpecialty(task.specialty) ?? agentRoleMeta(task.role).shortLabel}</small>
                </div>
                <StatusBadge status={task.status} />
              </div>
              <div className="agent-card-grid">
                <div className="agent-purpose">
                  <span>主要职责</span>
                  <p>{agentRoleMeta(task.role).description}</p>
                  <small>预期交付：{agentRoleMeta(task.role).deliverable}</small>
                </div>
                <div className="agent-assignment">
                  <span>本步任务</span>
                  <h4>{task.title}</h4>
                  {task.resultSummary ? (
                    <>
                      <p className="agent-result-summary">{task.resultSummary}</p>
                      {task.resultSummary.length > 220 && (
                        <details className="agent-result-details">
                          <summary>查看完整执行结果</summary>
                          <pre>{task.resultSummary}</pre>
                        </details>
                      )}
                    </>
                  ) : <p className="pending-copy">等待智能体提交执行结果。</p>}
                  {task.failure && <p className="failure-text">{task.failure}</p>}
                </div>
              </div>
              <div className="agent-row-footer">
                <span>最近更新 <time dateTime={task.updatedAt}>{formatTime(task.updatedAt)}</time></span>
                <span>执行尝试 {task.attempt}/{task.maxAttempts}</span>
                <span className="mono" title="任务编号">#{task.id.slice(0, 8)}</span>
              </div>
            </div>
          </article>
        ))}
        {tasks.length === 0 && (
          <div className="empty-panel flow-empty">
            <BrainCircuit size={24} aria-hidden="true" />
            <strong>正在等待调度计划</strong>
            <span>任务创建后，这里会显示每位智能体的中文职责与实时进展。</span>
          </div>
        )}
      </div>
    </section>
  )
}
