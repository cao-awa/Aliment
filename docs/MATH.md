[English Version](MATH.md) | [中文版本](MATH_zh.md)

# The Mathematical Model

Aliment's physiology is a **discrete-time dynamical system**: one state vector per player, advanced one
step per server tick. This document states every equation it is built from, the constant in each one,
and the reasoning behind the shape of it.

It is written for someone who wants to *change* the model rather than read about it. Where
[PHYSIOLOGY.md](PHYSIOLOGY.md) describes what each quantity means clinically and
[SPOILER.md](SPOILER.md) describes what a player experiences, this one is the algebra: what is a
function of what, which terms are additive and which are multiplicative, and what happens at the
boundaries.

Formulas are typeset as **LaTeX** in `$…$` and `$$…$$`, which renders on GitHub, in VS Code's Markdown
preview and in most Markdown viewers. Every equation names the source constant it comes from in
`typewriter`, so a claim here can be checked against the code in one search.

Everything here lives in two files, and nothing else:

| File | Contains |
| --- | --- |
| `src/main/scala/.../physiology/model/Model.scala` | The state types, the reference ranges, and `ModelConstants` - every scalar |
| `src/main/scala/.../physiology/model/Physiology.scala` | Every equation, and the local rates that are not in `ModelConstants` |

Neither imports Minecraft, Kotlin or Fabric. The model is a **pure function of numbers**, which is
what makes it testable without a world and what the numbers below rely on.

---

## 1. Conventions

### 1.1 Discrete time

The unit of time is **one server tick**, and a Minecraft day is

$$T_{\text{day}} = 24\,000 \text{ ticks}.$$

Every rate in the model is expressed per tick, and most are written as an exact quotient so the
headline figure stays legible rather than being frozen as a decimal:

$$r_w = \frac{100}{5 \times 24\,000} \quad (\texttt{WATER\_DECAY\_PER\_TICK})$$

That is deliberate. *100 over five days* states the intent and survives a change to the tick rate; a
literal does not. Where a rate is quoted below as a per-tick figure, the derivation is given beside
it.

The integration scheme is **explicit Euler**: each step reads the state at the start of the tick and
writes the state at the end,

$$\mathbf{s}_{t+1} = F(\mathbf{s}_t),$$

with no sub-stepping and no solver. This is appropriate because every rate is many orders of magnitude
below the stability limit of the fastest quantity in the model (see §18), so the difference between
Euler and an exact solution is far below the resolution of anything a player or a test can observe.

### 1.2 Units

| Quantity | Symbol | Unit | Note |
| --- | --- | --- | --- |
| Electrolytes | $\mathrm{Na}, \mathrm{K}, \mathrm{Mg}, \mathrm{Cl}, \mathrm{Ca}$ | mmol/L | |
| Trace elements | $I, A$ | µmol/L | iodine and vitamin C - a thousandth of a mmol, because their whole range is a fraction of one |
| Blood glucose | $g$ | mmol/L | |
| Insulin | $n$ | index | $1$ is fasting, $60$ is the cap |
| Inflammation | $J$ | 0..100 index | not a concentration; see §4 |
| Pathogen load | $\lambda$ | 0..100 index | bacteria $b$ and virus $v$ tracked separately, on a shared scale |
| Temperature | $\Theta$ | °C | on the body's own scale, not the biome's |
| Water | $w$ | 0..200 index | $100$ is normal, $10$ water is one thirst cell |
| Drugs | $\mathbf{d}$ | dose units | each has its own cap; only ratios are meaningful |
| Pyrogen | $\pi$ | °C | a set-point offset, $-6 \le \pi \le +6$ |
| CYP3A4 | $\varepsilon$ | 0..100 index | |

### 1.3 The two primitives

Nearly every equation in the model is built from one of these two.

**Saturation into a range.** This appears at the end of almost every step, so that no runaway value
can reach nonsense:

$$\mathrm{clamp}(x,\, a,\, b) = \begin{cases} a, & x < a \\[2pt] x, & a \le x \le b \\[2pt] b, & x > b \end{cases}$$

**One step towards a target.** Move a fraction $k$ of the remaining distance each tick:

$$\mathrm{approach}(x;\, \mu,\, k) = \mathrm{clamp}\big(x + (\mu - x)\,k,\; 0,\; 100\big)$$

Writing $e_t = \mu - x_t$ for the gap, the recursion is $e_{t+1} = e_t (1 - k)$, so

$$e_n = e_0 (1-k)^n .$$

It never overshoots for $0 < k < 1$, and it has a **time constant of $\tau = 1/k$ ticks**:

$$(1-k)^{1/k} \xrightarrow[\;k \to 0\;]{} e^{-1} \approx 0.368,$$

so after $1/k$ ticks roughly $63\%$ of the gap is closed and after $3/k$ about $95\%$. Where the model
wants a quantity to *seek* a value rather than jump to it, this is the shape used, and the multiplier
is chosen from the time constant that is wanted: $k = 0.002$ gives $500$ ticks ($25$ s),
$k = 0.0005$ gives $2\,000$ ticks, $k = 0.0004$ gives $2\,500$ ticks.

One naming caution: the *named helper* `approach` in `Physiology.scala` is mediator-only - it has
`MEDIATOR_APPROACH` baked in and clamps to `MediatorLevels.MAX` $= 100$. Insulin (§12.1), temperature
(§8.2) and the minerals (§10) use this same exponential shape with their own constant and their own
bounds, written inline. Read $k$ and the clamp as parameters, not as properties of the helper.

A third building block appears wherever a rate is specified as a *total* over a period - a drug that
is "gone in a day" from its cap $C$:

$$r = \frac{C}{T} \quad\text{e.g.}\quad r_{\text{eph}} = \frac{5}{24\,000} \quad (\texttt{EPHEDRINE\_DECAY\_PER\_TICK})$$

Note that this is **linear** (zeroth-order) elimination, not exponential: a constant amount per tick
regardless of what is left. §13 says which drugs use which and why.

---

## 2. The state vector

The entire input and output of the model is one vector; everything else is derived.

$$\mathbf{s} = \big(\underbrace{H, P, L, C, B}_{\text{mediators}},\; b,\, v,\; w,\; \underbrace{\mathrm{Na}, \mathrm{K}, \mathrm{Mg}, \mathrm{Cl}, \mathrm{Ca}}_{\text{electrolytes}},\; \underbrace{I, A}_{\text{trace}},\; \Theta,\; \pi,\; \alpha,\; \mathbf{d},\; \varepsilon,\; g,\; n\big)$$

| Component | Symbol | Range | Meaning |
| --- | --- | --- | --- |
| Mediators | $H, P, L, C, B$ | each $0..100$ | histamine, prostaglandin, leukotriene, cytokine, bradykinin |
| Bacteria, virus | $b, v$ | each $0..100$ | tracked separately |
| Water | $w$ | $0..200$ | $100 = $ normal |
| Electrolytes | $\mathrm{Na}, \mathrm{K}, \mathrm{Mg}, \mathrm{Cl}, \mathrm{Ca}$ | mmol/L | see §3 |
| Trace elements | $I, A$ | µmol/L | iodine, vitamin C |
| Temperature | $\Theta$ | °C | |
| Pyrogen | $\pi$ | $-6..+6$ | injected set-point offset |
| Immune latch | $\alpha$ | $\{0, 1\}$ | has active immunity engaged? |
| Drugs | $\mathbf{d}$ | | $12$ compounds |
| Enzymes | $\varepsilon$ | $0..100$ | CYP3A4 activity |
| Glucose | $g$ | $0..30$ | mmol/L |
| Insulin | $n$ | $0..60$ | index |

Written out as the constructor's defaults: $\alpha = 0$ (false), $\mathbf{d} = \mathbf{0}$,
$\varepsilon = 85$, $g = 5$, $n = 1$, and the mediators at rest (§4).

Two structural decisions in that vector are load-bearing:

- **Inflammation is not a stored component.** It is $J(\mathbf{m})$, a weighted sum *computed* from
  the five mediators on every read (§4). This is what lets salicin and dexamethasone differ: they act
  on different mediators, and the index is a consequence rather than a variable they both write to.
- **$\varepsilon$ is a sibling of $\mathbf{d}$, not a member.** A drug is something the body is
  carrying and clearing; an enzyme index is how fast a clearance pathway is running. CYP3A4 is a pure
  function of naringin (§14), so it is not an independent quantity with a life of its own.

---

## 3. Reference ranges and the deviation functions

Each mineral carries the thresholds a real blood test would report:

$$\min \;<\; \ell_{\text{sev}} \;<\; \ell \;<\; x_n \;<\; u \;<\; u_{\text{sev}} \;<\; \max$$

where $\ell, u$ are the safe bounds and $\ell_{\text{sev}}, u_{\text{sev}}$ the severe ones. The four
intervals are, in order: **severe**, **mild**, **healthy**, **mild**, **severe**.

| Mineral | Symbol | Unit | $x_n$ | $\ell$ | $u$ | $\ell_{\text{sev}}$ | $u_{\text{sev}}$ | $\min$ | $\max$ |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Sodium | $\mathrm{Na}$ | mmol/L | 140 | 135 | 145 | 125 | 150 | 100 | 190 |
| Potassium | $\mathrm{K}$ | mmol/L | 4.2 | 3.5 | 5.0 | 3.0 | 6.0 | 1.5 | 9 |
| Magnesium | $\mathrm{Mg}$ | mmol/L | 0.85 | 0.70 | 1.00 | 0.50 | 1.50 | 0.2 | 3 |
| Chloride | $\mathrm{Cl}$ | mmol/L | 101 | 96 | 106 | 90 | 115 | 70 | 140 |
| Calcium | $\mathrm{Ca}$ | mmol/L | 2.35 | 2.10 | 2.60 | 1.80 | 3.00 | 1 | 4 |
| Iodine | $I$ | µmol/L | 0.50 | 0.25 | 0.80 | 0.20 | 1.20 | 0.05 | 2 |
| Vitamin C | $A$ | µmol/L | 60.0 | 40.0 | 80.0 | 15.0 | 120.0 | 0 | 150 |

Two derived functions do most of the work downstream. The absolute deviation is the distance outside
the safe band, zero inside it:

$$d(x) = \begin{cases} \ell - x, & x < \ell \\[2pt] 0, & \ell \le x \le u \\[2pt] x - u, & x > u \end{cases}$$

and the **relative** deviation expresses that distance as a fraction of the mineral's own normal value:

$$\rho(x) = \frac{d(x)}{x_n}$$

$d$ is in the mineral's own units. The division by $x_n$ is the only reason deviations can be compared
across minerals at all: **$0.2$ is nothing to sodium and a catastrophe for iodine**. Anything that
ranks or combines minerals - the worst-imbalance readout, the "worst offender" direction - uses
$\rho$.

$\mathrm{isSevere}$ is $x < \ell_{\text{sev}}$ or $x > u_{\text{sev}}$; the direction sign is
$\operatorname{sgn}(x - x_n)$ restricted to the reference band; and saturation is to
$[\min, \max]$.

---

## 4. Inflammation: the composite index

$$J(\mathbf{m}) = \mathrm{clamp}\Big(0.15\,H + 0.20\,P + 0.15\,L + 0.35\,C + 0.15\,B,\; 0,\; 100\Big)$$

The weights sum to exactly $1$:

$$\sum_i w_i = 0.15 + 0.20 + 0.15 + 0.35 + 0.15 = 1.00,$$

and cytokines carry the largest share ($0.35$) because they are the systemic driver. The consequence
is that $J$ is on the same $0..100$ scale as the mediator concentrations themselves, so a body with
every mediator at $25$ reads $25$.

The resting set is chosen to be the fixed point of that map:

| Mediator | $w_i$ | Resting | Contribution $w_i m_i$ |
| --- | --- | --- | --- |
| Histamine | 0.15 | 25 | 3.75 |
| Prostaglandin | 0.20 | 30 | 6.00 |
| Leukotriene | 0.15 | 30 | 4.50 |
| Cytokine | 0.35 | 20 | 7.00 |
| Bradykinin | 0.15 | 25 | 3.75 |
| **Total** | **1.00** | | **25.00** |

so the resting vector gives $J = 25$ exactly, the middle of the safe band $20..30$:

$$J(\mathbf{m}_{\text{rest}}) = \texttt{BASELINE\_INFLAMMATION} = 25.$$

That is not a coincidence to be maintained by hand: it is the definition of the resting set, and
changing a weight or a resting level without the other moves the baseline.

The constants built on the index:

| Constant | Value | Meaning |
| --- | --- | --- |
| `SAFE_INFLAMMATION_LOW` / `HIGH` | 20 / 30 | the healthy band |
| `BASELINE_INFLAMMATION` | 25 | the fixed point above |
| `IMMUNOSUPPRESSION_THRESHOLD` | 12 | at or below, immunity has given up |
| `IMMUNE_STORM_THRESHOLD` | 75 | at or above, the response hurts the host |
| `IMMUNE_SHAKE_ONSET` | 75 | the same number; see §15.3 |

---

## 5. Immune competence

How well the current inflammation actually fights pathogens, on $[0, 1]$:

$$K(J) = \exp\!\left(-\frac{\delta(J)^2}{2\,\sigma(J)^2}\right) \cdot \mathrm{clamp}\!\left(\frac{J}{12},\, 0,\, 1\right)$$

with the plateau-relative distance and the piecewise width

$$\delta(J) = \begin{cases} J - 20, & J < 20 \\[2pt] 0, & 20 \le J \le 40 \\[2pt] J - 40, & J > 40 \end{cases}
\qquad
\sigma(J) = \begin{cases} 10, & J < 20 \quad (\texttt{COMPETENCE\_SIGMA\_LOW}) \\[2pt] 15, & J \ge 20 \quad (\texttt{COMPETENCE\_SIGMA}) \end{cases}$$

This is a **half-Gaussian plateau**: $K = 1$ exactly across $20 \le J \le 40$, falls away on both sides,
and is multiplied by a linear ramp $J/12$ that goes to zero at $J = 0$. The asymmetry is deliberate,
and it is the point of the whole function:

| $J$ | bell | ramp | $K$ | Reading |
| --- | --- | --- | --- | --- |
| 0 | 0.135 | 0.00 | **0.000** | immunosuppressed: no defence at all |
| 6 | 0.375 | 0.50 | **0.188** | |
| 12 | 0.726 | 1.00 | **0.726** | the immunosuppression threshold |
| 20–40 | 1.000 | 1.00 | **1.000** | the plateau: healthy |
| 75 | 0.066 | 1.00 | **0.066** | a storm is nearly as useless as nothing |
| 100 | 0.022 | 1.00 | **0.022** | |

The two ends fail for different reasons and the curve says so. Below $20$ the immune system is
*under*-reacting - hence the tighter $\sigma = 10$ - and the $J/12$ ramp takes it to zero, so a body
driven immunosuppressed by a drug overdose cannot clear anything. Above $40$ it is *over*-reacting:
dysregulated, fighting itself, and clearing worse the higher it goes, with a wider $\sigma = 15$.

The consequence is the model's central tension. **Both too little inflammation and too much of it let
the infection grow**, and the band that works is narrow. That is what makes salicin and dexamethasone
a decision rather than a win button - they damp the response, which is good while the response is
above the plateau and actively harmful once the damping pushes it below.

---

## 6. Mediator kinetics

Each tick, every mediator moves one `approach` step (§1.3) towards its own target. The targets share
a common structure: a baseline term scaled by overdose, plus a response term driven by the pathogen.

### 6.1 The pathogen stimulus

A single scalar converts pathogen load into the size of the immune response. It is piecewise, with
three regions:

$$S(\lambda, \alpha) = \begin{cases}
0, & \alpha = 0,\; \lambda \le 20 \\[4pt]
0.18\,\dfrac{\lambda}{20}, & \alpha = 1,\; \lambda \le 20 \\[8pt]
\mathrm{clamp}\!\left(\dfrac{\lambda - 20}{55 - 20},\, 0,\, 1\right), & 20 < \lambda \le 55 \\[8pt]
1 + 2.5\,\mathrm{clamp}\!\left(\dfrac{\lambda - 55}{100 - 55},\, 0,\, 1\right), & \lambda > 55
\end{cases}$$

| Load | Condition | $S$ |
| --- | --- | --- |
| $0\text{–}20$ | immunity not yet engaged | $0$ - the body has not noticed |
| $0\text{–}20$ | immunity engaged | $0.18\,\lambda/20$ - up to $0.18$ |
| $20\text{–}55$ | | $(\lambda-20)/35$, ramping $0 \to 1$ |
| $55\text{–}100$ | | $1 + 2.5(\lambda-55)/45$, ramping $1 \to 3.5$ |

Two things are worth stating plainly.

The first region is a **dead zone**: below the activation load, with immunity not yet engaged, the
response is exactly zero. An infection that never gets past $20$ therefore never provokes anything -
which is why `IMMUNITY_ACTIVATION_LOAD` $= 20$ is the number that decides whether a given meal makes
the player ill or not.

The second, above $55$, is the **storm region**: the response is no longer proportional to the load
but rises to $3.5\times$ it, so a severe infection produces a response several times larger than a
linear one. This is what drives cytokine up and, through it, the fever and the storm symptoms.

> **A discontinuity worth knowing about.** At exactly $\lambda = 20$ with immunity engaged, the second
> branch is taken and $S = (20/20)\cdot 0.18 = 0.18$. One tick later, at $\lambda = 20.001$, the third
> condition fails and the value drops to $(20.001-20)/35 \approx 2.86\times10^{-5}$. The stimulus
> therefore **falls off a cliff as the load grows past $20$** while immunity is active - from $0.18$ to
> nearly nothing in one tick - and jumps back up to $0.18$ as the load falls below $20$ again:
>
> $$\lim_{\lambda \to 20^-} S = 0.18, \qquad \lim_{\lambda \to 20^+} S \approx 0 .$$
>
> It is a real seam in the piecewise function, not an artefact of reading it wrong, and it is only
> visible in the narrow case of a body whose immunity is already engaged and whose infection is right
> at the activation load.
>
> The direction matters for what a player sees. Because the drop happens as the load *increases*, the
> commitment point is the moment the infection crosses $20$: below it the response is already fully
> engaged at $0.18$, and the instant it crosses, that response **collapses** and has to rebuild from
> zero along the $20 \to 55$ ramp. A body whose immunity is already engaged is better off - in
> mediator terms - staying just under $20$ than sitting just over it.

### 6.2 Drug damping and overdose

Salicin and dexamethasone both suppress, but they are not interchangeable, and the arithmetic is why.
Each is first expressed as a saturating multiple of its effective concentration:

$$u_s = \mathrm{clamp}\!\left(\frac{[\text{salicin}]}{E_s},\, 0,\, 1.5\right), \qquad
u_d = \mathrm{clamp}\!\left(\frac{[\text{dexamethasone}]}{E_d},\, 0,\, 1.5\right)$$

with $E_s = E_d = 1$ (`SALICIN_EFFECTIVE`, `DEXAMETHASONE_EFFECTIVE`) and the cap
$1.5$ (`DRUG_MAX_SUPPRESSION`). From those, three derived quantities:

$$f = \max\big(\min(u_s, 1),\; \min(u_d, 1)\big) \qquad\text{(the ``fight'' term)}$$

$$D = \max(1 - 0.88 f,\; 0.05) \qquad\text{(damping)}$$

$$O = \max(u_s - 1, 0) + \max(u_d - 1, 0), \qquad
\beta = \max(1 - 1.8\,O,\; 0) \qquad\text{(overdose, baseline scale)}$$

Three design decisions are encoded here:

- **$f$ is a maximum, not a sum.** A combination damps more than either alone (because the two have
  different extra effects below) but they do not stack into a stronger *general* damping. With either
  drug at or above its effective concentration, $f = 1$ and $D = 1 - 0.88 = 0.12$: an $88\%$ cut.
- **There is a floor.** $D \ge 0.05$, so the pathogen-driven response can never be cut to zero
  however much is given.
- **Overdose attacks the baseline instead.** Past the effective concentration the *resting* levels are
  scaled down, by $1.8$ per unit of excess: at the cap (salicin $3$, dexamethasone $2$) both
  suppression values reach $1.5$, so $O = 1.0$ and $\beta = 0$. The resting mediators then target zero
  and the inflammation collapses into the single digits - which is exactly where $K(J)$'s ramp bites,
  and why an overdose makes the infection worse.

### 6.3 The five targets

$$\begin{aligned}
C^* &= 20\,\beta + 100\,S\,D\,\big(1 - 0.4\min(u_d,1)\big) \\[4pt]
H^* &= 25\,\beta + 45\,S\,D \\[4pt]
B^* &= 25\,\beta + 60\,S\,D \\[4pt]
P^* &= 20\,\beta + \big(0.5\,C + 25\,S\big)\,D\,\big(1 - 0.5\min(u_s,1)\big) \\[4pt]
L^* &= 20\,\beta + \big(0.5\,C + 20\,S\big)\,D\,\big(1 - 0.4\min(u_d,1)\big)
\end{aligned}$$

The `BASE` constants are **not** simply a copy of the resting set, and the difference is the most
easily-missed piece of arithmetic in the model:

| Mediator | $B_i$ (`BASE`) | Resting | Difference | Where it comes from |
| --- | --- | --- | --- | --- |
| Histamine | 25 | 25 | 0 | - |
| Bradykinin | 25 | 25 | 0 | - |
| Cytokine | 20 | 20 | 0 | - |
| Prostaglandin | **20** | **30** | $-10$ | $+\,0.5\,C$ |
| Leukotriene | **20** | **30** | $-10$ | $+\,0.5\,C$ |

Prostaglandin and leukotriene are each written **ten points below** where the body is supposed to sit,
and the gap is closed by the $0.5\,C$ term. At rest $C = 20$, so

$$B_P + 0.5\,C = 20 + 0.5 \times 20 = 30 = P_{\text{rest}},$$

which is why the resting set is a fixed point even though two of the five bases disagree with it. It is
not a coincidence of the numbers either - the coupling constants satisfy

$$\texttt{PROSTAGLANDIN\_FROM\_CYTOKINE} = \texttt{LEUKOTRIENE\_FROM\_CYTOKINE} = 0.5
= \frac{P_{\text{rest}} - B_P}{C_{\text{rest}}} = \frac{30 - 20}{20}.$$

Change either the resting level or one of those two bases without the other and the body stops sitting
at an inflammation of $25$.

So with no pathogen ($S = 0$) and no overdose ($\beta = 1$) every mediator targets its resting value
and the system sits still at $J = 25$.

Four structural points:

- **Cytokine is computed first and fed into the other two.** Prostaglandin and leukotriene each take
  $0.5\,C$, representing induced COX-2 and 5-lipoxygenase downstream of the cytokine signal. This is
  why the targets are ordered that way in the code - the $C$ used is the *newly approached* value, not
  the previous tick's.
- **The drug-specific extras are subtractive multipliers on the response, not on the baseline.**
  Dexamethasone cuts the cytokine response by a further $40\%$ and the leukotriene response by $40\%$;
  salicin cuts the prostaglandin response by $50\%$. This is the distinction that makes them different
  drugs: **salicin is the antipyretic** (prostaglandin is what raises the set point, §8) and
  **dexamethasone is the broad immunosuppressant**.
- **The extras multiply $S$, which is why an overdose must work through $\beta$.** A multiplier on a
  term that is already zero does nothing.
- **The response scales are ordered** $100 > 60 > 45 > 25 > 20$ for cytokine, bradykinin, histamine,
  prostaglandin and leukotriene, so a storm's inflammation is dominated by cytokine, which carries the
  $0.35$ weight as well. The two amplify each other by construction.

Every one of the five then takes one `approach` step with `MEDIATOR_APPROACH` $= 0.002$, a time
constant of **$500$ ticks ($25$ seconds)**.

---

## 7. Pathogen dynamics

Bacteria and viruses are tracked separately and identically; only the drug that suppresses each
differs. Both go through `stepTargetedPathogen`.

### 7.1 Growth and clearance

Growth is logistic, with rate $r_g$ and carrying capacity $\Lambda = 100$:

$$G(\lambda) = r_g\,\lambda\left(1 - \frac{\lambda}{\Lambda}\right), \qquad r_g = 0.0004
\quad(\texttt{GROWTH\_RATE})$$

Per tick at $\lambda = 50$ that is $0.0004 \times 50 \times 0.5 = 0.01$, i.e. about $+1\%$ of the cap
per $100$ ticks. $G$ is largest in the middle of the range and goes to zero at both ends, which is what
stops an infection from either stalling when small or running away when large.

Clearance has **two terms**, and they matter separately:

$$X(\lambda) = \begin{cases}
G(\lambda)\,K + r_c\,K, & \alpha = 1 \;\text{or}\; \lambda > 20 \\[4pt]
0, & \text{otherwise}
\end{cases}
\qquad
r_c = \frac{20}{2 \times 24\,000} = \frac{1}{2400} \approx 4.167\times10^{-4}
\quad(\texttt{CLEARANCE\_BASE\_RATE})$$

- $G(\lambda)\,K$ - clearance proportional to how much there is, with the *same* shape as growth. This
  term alone would exactly cancel growth at $K = 1$, holding the load flat.
- $r_c K$ - a **constant** term, which is what actually drives the load to zero. At full competence it
  clears a load of $20$ in exactly **$2$ in-game days**.

The update is then

$$\lambda' = \mathrm{clamp}\big(\lambda + G(\lambda) - X(\lambda),\; 0,\; \Lambda\big).$$

Clearance is **switched off entirely** below the activation load unless immunity is already engaged.
That is the same dead zone as §6.1, from the other side.

### 7.2 Drug suppression

The two thresholds per drug are $s = 1.5$ (`SLOW`) and $q = 3.0$ (`SUPPRESS`); berberine targets
bacteria and glycyrrhizin targets viruses. Writing $\upsilon$ for the drug concentration and

$$\sigma = \mathrm{clamp}\!\left(\frac{\upsilon - s}{q - s},\, 0,\, 1\right)$$

for how far into the middle band it sits:

$$\lambda' = \begin{cases}
\mathrm{clamp}\Big(\lambda - X - r_q\dfrac{\upsilon}{q},\; 0,\; \Lambda\Big), & \upsilon \ge q \\[10pt]
\mathrm{clamp}\Big(\lambda + G\,(1 - 0.75\,\sigma) - X,\; 0,\; \Lambda\Big), & s < \upsilon < q \\[8pt]
\mathrm{clamp}\big(\lambda + G - X,\; 0,\; \Lambda\big), & \upsilon \le s
\end{cases}$$

with the suppressed clearance rate

$$r_q = \frac{100}{1.5 \times 24\,000} \approx 2.78\times10^{-3}
\quad(\texttt{DRUG\_SUPPRESS\_CLEARANCE\_RATE}).$$

| Concentration | Behaviour |
| --- | --- |
| $\le 1.5$ | untreated: logistic growth against immune clearance |
| $1.5\text{–}3.0$ | growth reduced by up to **$75\%$**, letting immunity overpower it |
| $\ge 3.0$ | **replication stops outright** - the $+G$ term is gone from the sum - and an extra clearance of $r_q\,\upsilon/q$ is added |

Note the structural difference: in the middle branch the drug *scales the growth term*, and in the top
branch it *replaces* it with a clearance term. So $q$ is a genuine clinical threshold - above it the
infection can only go down - and reaching it is worth far more than edging up to it.

### 7.3 Immune activation hysteresis

Immunity latches on above the activation load and stays on until the pathogen is essentially gone:

$$\alpha' = \begin{cases}
\lambda > 0.0001, & \alpha = 1 \\[4pt]
\lambda > 20, & \alpha = 0
\end{cases}$$

This is **hysteresis**, and it exists so the $S$ dead zone does not chatter. A body that has engaged
its immune system keeps it engaged all the way down to a load of $0.0001$; a body that has not needs
the load to exceed $20$ to start. Without the latch, an infection hovering at $\lambda = 20$ would
switch the response on and off on alternate ticks.

---

## 8. Thermal regulation

### 8.1 The target

The fever term is driven by prostaglandin *above resting*:

$$P_{\text{exc}} = \max(P - 30,\, 0), \qquad \Phi_{\text{raw}} = 0.05\,P_{\text{exc}}
\quad(\texttt{FEVER\_PER\_PROSTAGLANDIN})$$

and it is **capped differently depending on the load**, with the boundary at
`IMMUNE_STRESS_LOAD` $= 55$:

$$\Phi = \begin{cases}
\min(\Phi_{\text{raw}},\; 2.5), & \lambda \le 55 \\[4pt]
\min(\Phi_{\text{raw}},\; 4.0), & \lambda > 55
\end{cases}$$

where $2.5 = $ `FEVER_NORMAL_IMMUNE_MAX` $- 37$ and $4.0 = $ `FEVER_MAX`. So an ordinary infection
tops out at $39.5$ °C and only past $55$ does the cap lift to $41.0$ °C: a high fever is not reachable
by an ordinary infection however much prostaglandin it makes - it requires a storm.

The set point is the sum of six independent contributions:

$$\Theta^* = \mathrm{clamp}\Big(37 + \Phi + \pi + \Delta_{\text{thy}}(I) + \Phi_{\text{anti}} + \Phi_{\text{psi}}
+0.6\,(\Theta_{\text{amb}} - 37),\; 30,\; 42\Big)$$

Six contributions, **all additive and all independent**. This is the model's clearest statement that
different causes of a raised temperature do not substitute for one another - a mandrake eaten while
febrile is worse than either alone, and the constants enforce that rather than merely permitting it.

| Term | Source | Range | Touched by salicin? |
| --- | --- | --- | --- |
| $\Phi$ | Prostaglandin above resting | $0 \to +2.5$, or $+4.0$ in a storm | **yes** - it is a prostaglandin term |
| $\pi$ | Injected (diagnostic command) | $-6 \to +6$ | no |
| $\Delta_{\text{thy}}$ | Iodine outside its range | $-0.8 \to +0.8$ | no |
| $\Phi_{\text{anti}}$ | Scopolamine + atropine | $0 \to +4.0$ | no |
| $\Phi_{\text{psi}}$ | Psilocin above the storm stage | $0 \to +4.0$ | no |
| $0.6(\Theta_{\text{amb}} - 37)$ | Ambient, thermoregulated | see §8.2 | no |

`FEVER_PER_PROSTAGLANDIN` $= 0.05$ means the resting prostaglandin of $30$ contributes nothing (the
term is measured *above* resting) and every $20$ points above resting adds $1$ °C.

**The thyroid term.** Note the division by $x_n = 0.5$, not by the width of any interval:

$$\Delta_{\text{thy}}(I) = \mathrm{clamp}\Big(2\,\delta_I,\; -0.8,\; +0.8\Big),
\qquad
\delta_I = \begin{cases}
\dfrac{I - 0.25}{0.5}, & I < 0.25 \\[8pt]
\dfrac{I - 0.80}{0.5}, & I > 0.80 \\[8pt]
0, & \text{otherwise}
\end{cases}$$

with $2 = $ `THYROID_SHIFT_PER_RELATIVE` and $0.8 = $ `THYROID_SHIFT_MAX`. Because the shift is
expressed relative to the mineral's own normal value it is a dimensionless fraction before being
scaled. At the hard floor ($I = 0.05$) the raw delta is $(0.05-0.25)/0.5 = -0.4$, giving $-0.8$ °C -
the cap is reached exactly at the bottom of the iodine scale:

$$2 \times (-0.4) = -0.8 = -\texttt{THYROID\_SHIFT\_MAX}.$$

**The two drug fevers are steps, not curves.** With $\upsilon_{\text{anti}} = $ scopolamine $+$
atropine:

$$\Phi_{\text{anti}} = \begin{cases}
0, & \upsilon_{\text{anti}} < 1.5 \\[4pt]
+1.0, & 1.5 \le \upsilon_{\text{anti}} < 2.5 \\[4pt]
+2.5, & 2.5 \le \upsilon_{\text{anti}} < 4.0 \\[4pt]
+4.0, & \upsilon_{\text{anti}} \ge 4.0
\end{cases}
\qquad\Longrightarrow\qquad
\Theta^* = 37,\; 38.0,\; 39.5,\; 41.0
$$

and with $\psi$ for psilocin:

$$\Phi_{\text{psi}} = \begin{cases}
0, & \psi \le 5.0 \\[4pt]
+2.0, & 5.0 < \psi < 7.0 \\[4pt]
+4.0, & \psi \ge 7.0
\end{cases}
\qquad\Longrightarrow\qquad
\Theta^* = 37,\; 39.0,\; 41.0$$

Both are **set-point shifts rather than fevers**, and the code comment is explicit that nothing in
either goes through prostaglandin - so salicin does nothing about them. An atropine overdose and an
infection fever add.

### 8.2 Approach and the ambient chain

The temperature follows its set point by the `approach` map with `TEMPERATURE_APPROACH` $= 0.0004$:

$$\Theta' = \mathrm{clamp}\Big(\Theta + (\Theta^* - \Theta)\cdot 0.0004,\; 30,\; 42\Big)$$

a time constant of **$2\,500$ ticks** (a little over two in-game minutes). The body therefore takes
minutes to reach a new set point, not an instant - which is what makes a fever read as a developing
illness.

The ambient temperature handed to the model is itself computed in the model, from the world's biome
temperature $T_{\text{biome}}$ and a few flags. First the raw pull:

$$\Pi = 2.0\,(T_{\text{biome}} - 0.8)
\;-\; 2.5\,\mathbb{1}[\text{wet}]
\;-\; 4.0\,\mathbb{1}[\text{powder snow}]
\;+\; \begin{cases} 6.0, & \text{lava} \\ 4.0, & \text{fire} \\ 0, & \text{otherwise} \end{cases}$$

with $0.8 = $ `TEMPERATE_BIOME` and $2.0 = $ `BIOME_PULL`. Then the **thermoneutral band** removes the
first two degrees at no cost:

$$\Pi_{\text{eff}} = \begin{cases}
\Pi - 2.0, & \Pi > 2.0 \\[4pt]
\Pi + 2.0, & \Pi < -2.0 \\[4pt]
0, & \text{otherwise}
\end{cases}
\qquad
\Theta_{\text{amb}} = \mathrm{clamp}(37 + \Pi_{\text{eff}},\; 30,\; 42)$$

The band is the important part: the first $2$ °C of pull cost nothing at all. A temperate biome is
$0.8$, so a desert at $\approx 2.0$ is $(2.0-0.8)\times 2 = 2.4$ of pull, which after the band is only
$0.4$ °C of *ambient* - and then only $60\%$ of that reaches the body. That is why the heat side of
the model is driven almost entirely by fever rather than by weather, and why a walk through a taiga is
not a medical event. The cold side has far more leverage: powder snow is $-4.0$ of pull.

### 8.3 Pyrogen

An injected pyrogen *is* a set-point offset, and it clears only once the fever it drives has actually
developed:

$$\pi' = \begin{cases}
0, & \pi = 0 \\[4pt]
\pi, & \lvert \Theta - \Theta^* \rvert \ge 0.2 \\[4pt]
\max(\pi - r_\pi,\, 0), & \pi > 0 \\[4pt]
\min(\pi + r_\pi,\, 0), & \pi < 0
\end{cases}
\qquad
r_\pi = \frac{6}{24\,000} = 2.5\times10^{-4}
\quad(\texttt{PYROGEN\_DECAY\_PER\_TICK})$$

`PYROGEN_SETTLED` $= 0.2$ °C. Without the wait, the offset would fall away underneath a body still
climbing towards it and the fever would peak short of what was asked - asking for $39.5$ used to peak
at $38.8$. With it, the peak lands on the request exactly. Decay is linear and symmetric for positive
and negative offsets (a negative pyrogen is an antipyretic).

`induceFever` inverts the whole thing, paying for the settled tolerance so the peak is exact. Given a
requested absolute temperature $\Theta_{\text{req}}$:

$$\Theta_0 = \Theta^*\big|_{\pi = 0}, \qquad
\pi_{\text{naive}} = \Theta_{\text{req}} - \Theta_0, \qquad
\pi_{\text{needed}} = \pi_{\text{naive}} + 0.2\,\operatorname{sgn}(\pi_{\text{naive}})$$

$$\pi \leftarrow \mathrm{clamp}(\pi_{\text{needed}},\; -6,\; +6) \quad(\texttt{PYROGEN\_CAP})$$

An infection never goes through any of this: its fever comes from prostaglandin, so $\pi$ stays zero
and `stepPyrogen` returns immediately.

---

## 9. Hydration

Let

$$w^+ = \max(w - 100,\, 0)$$

be the excess above normal. The loss per tick is the base drain, amplified by over-hydration and by two
electrolyte excesses, plus sweat:

$$\ell = r_w\big(1 + 0.01\,w^+\big)\,\mu_{\mathrm{Na}}\,\mu_{\mathrm{Ca}} \;+\; \sigma(\Delta\Theta)$$

$$r_w = \frac{100}{5 \times 24\,000}, \qquad
\mu_{\mathrm{Na}} = \begin{cases} 1.3, & \mathrm{Na} > 145 \\ 1, & \text{otherwise}\end{cases}, \qquad
\mu_{\mathrm{Ca}} = \begin{cases} 1.2, & \mathrm{Ca} > 2.60 \\ 1, & \text{otherwise}\end{cases}$$

$$w' = \mathrm{clamp}(w - \ell,\; 0,\; 200)$$

The sweat term is a **downward parabola with a dead zone**: negative at first, crossing zero at
$\Delta\Theta = 1.25$, then positive and accelerating.

$$\Delta\Theta = \max(\Theta - 37,\, 0), \qquad
\sigma(\Delta) = \begin{cases}
\max\big(a\Delta + b\Delta^2,\; 0\big), & \Delta > 1.25 \\[4pt]
0, & \Delta \le 1.25
\end{cases}$$

$$a = -\frac{1}{3360} \quad(\texttt{SWEAT\_A}), \qquad b = \frac{1}{4200} \quad(\texttt{SWEAT\_B})$$

It is written as a quadratic because the calibration points demand it, and the two points give two
equations, which is exactly what fixes the two coefficients:

| $\Delta\Theta$ | Core temp | Sweat $\sigma$ | Total loss/tick | $100$ water lasts |
| --- | --- | --- | --- | --- |
| $\le 1.25$ | $\le 38.25$ | $0$ | $1/1200$ | **5 days** |
| $2$ | $39.0$ | $1/2800$ | $1/840$ | **3.5 days** |
| $3$ | $40.0$ | $1/800$ | $1/480$ | **2.0 days** |

The root of the parabola is worth checking, because it is what makes the guard exactly right:

$$a + b\Delta = 0 \;\Longrightarrow\; \Delta = -\frac{a}{b} = \frac{4200}{3360} = 1.25 .$$

The same parabola is *negative* below $1.25$, so the guard $\Delta > 1.25$ is not a convenience, it is
load-bearing: without it a fever below $38.25$ °C would make the body *gain* water.

Two electrolyte excesses multiply the loss (hypernatraemia and hypercalcaemia both cause thirst and
increased urine output), and over-hydration doubles the base rate at the ceiling, since $w^+$ reaches
$100$ there:

$$1 + 0.01 \times 100 = 2 .$$

---

## 10. Electrolytes

Each of the five minerals is stepped independently through the same function:

$$x' = \mathrm{clamp}\Big(x + (x_n - x)\,h \;-\; L\,\varepsilon_x \cdot \frac{x}{x_n}\cdot x_n,\;\; x_{\min},\; x_{\max}\Big)$$

with $h = 0.00005$ (`ELECTROLYTE_HOMEOSTASIS`). Two forces, and they are structurally different:

- **Homeostasis** - a proportional controller towards $x_n$, time constant $1/h = 20\,000$ ticks
  (under one game day). This is what makes every mineral settle at a steady state rather than drifting
  off to an extreme: whatever $L$ does, this pulls back.
- **Excretion** - the third term, which the source writes as
  $L\,\varepsilon_x\,(x/x_n)\,x_n$. Because the concentration factor and the normal value cancel, that
  product is exactly

  $$L\,\varepsilon_x \cdot \frac{x}{x_n}\cdot x_n = L\,\varepsilon_x\,x,$$

  so the term is **proportional to the value itself**. The filtered load a kidney handles is the blood
  concentration times the volume cleared, so a high electrolyte is dumped faster than a low one, and
  the same $L$ empties a full store quicker than a nearly empty one. At $x = x_n$ the concentration
  factor is exactly $1$, so the leak between two healthy values is the reference leak $L\varepsilon_x x_n$.
  (The source spells it out in the long form because the *reading* - concentration times normal - is
  the physiology; the cancellation is just what makes the arithmetic cheap.)

The relative urinary loss per mineral is what makes a saline drink replace salt and not everything
else:

| Mineral | $\varepsilon_x$ |
| --- | --- |
| Sodium | 1.0 |
| Chloride | 1.0 |
| Potassium | 0.7 |
| Magnesium | 0.4 |
| Calcium | 0.4 |

The shared $L$ is the sum of the two water-loss mechanisms, both unit-free by construction:

$$L = \underbrace{9\times10^{-6}\,\min\!\left(\frac{w^+}{100},\, 1\right)}_{\text{flush}}
\;+\; \underbrace{\begin{cases} 2\times10^{-6}\,\Delta\Theta, & \Delta\Theta > 1.25 \\ 0, & \Delta\Theta \le 1.25 \end{cases}}_{\text{sweat}}$$

`mineralFlush` saturates at the top of the water scale; `mineralSweat` reuses the same $1.25$ °C dead
zone as the water sweat term, so the body never sweats salt without sweating water. Everything is a
fraction of a mineral's **normal concentration**, which is why the same equation works for sodium at
$140$ mmol/L and iodine at $0.5$ µmol/L.

---

## 11. Trace elements

Iodine and vitamin C are handled separately because neither is regulated towards a set point. The body
cannot make either, so what is not eaten is lost.

### 11.1 Iodine - a flat leak plus an excess term

$$I' = I - \Big[\big(f_d + 0.7\,L\big)\,x_n^{I} \;+\; c_e \max(I - x_n^{I},\, 0)\Big]$$

$$f_d = \frac{(0.5 - 0.05)/(15 \times 24\,000)}{0.5} = 2.5\times10^{-6}
\quad(\texttt{IODINE\_DRAIN\_FRACTION}), \qquad c_e = 6\times10^{-6}
\quad(\texttt{IODINE\_EXCESS\_CLEARANCE})$$

The design point is that the leak is **flat**, not proportional:

- The ordinary loss is $2.5\times10^{-6} \times 0.5 = 1.25\times10^{-6}$ per tick, so a full store falls
  from $0.50$ to the hard floor $0.05$ in exactly **$15$ in-game days**:

  $$\frac{0.50 - 0.05}{1.25\times10^{-6}} = 360\,000 \text{ ticks} = 15 \times 24\,000 .$$

  That is an exact figure rather than an asymptote, because a constant drain from a fixed start takes a
  fixed time.
- A **proportional controller towards normal is deliberately absent.** It is exactly what would stop the
  store from ever running out, and iodine is meant to be a dietary need rather than a bonus.
- The excess term is written against the **excess above normal**, not against the whole value. A leak
  scaling with the value itself would bend the depletion curve, and "fifteen days from normal to the
  floor" is a figure both the tests and the docs state.

The two constants that look contradictory are not. The ordinary leak was slowed to a third ($f_d$),
while a surplus clears **three times as fast** as it first did ($c_e$). The two changes pull in
opposite directions on purpose: a body that is merely getting by keeps its iodine far longer, while one
carrying far too much sheds it quickly, and the band between settles.

Sweating ($0.7L$) carries iodine off on top of the leak, which is what pushes an otherwise adequate
diet under the reference range during a long fever.

### 11.2 Vitamin C - first-order elimination

$$A' = \mathrm{clamp}\Big(A\,(1 - k_A),\; 0,\; 150\Big)$$

with the rate derived from the half-life rather than the other way round:

$$k_A = \frac{\ln 2}{5 \times 24\,000} \approx 5.776\times10^{-6}
\quad(\texttt{VITAMIN\_C\_DECAY\_RATE}), \qquad T_{1/2} = 5 \times 24\,000 \text{ ticks}$$

This is the one place the model uses **true first-order (exponential) kinetics** rather than the linear
form of §1.3, and the constant is derived from the half-life rather than the reverse. Solving
$(1-k_A)^{T_{1/2}} = \tfrac12$ gives back the stated half-life exactly, which is what makes the
headline figure exact: $80.0 \to 40.0$ in precisely $5$ in-game days, $40.0 \to 20.0$ in the next $5$,
and so on - a straight line on a logarithmic axis, not on a linear one:

$$\ln A(t) = \ln A(0) - k_A t .$$

Note this is the opposite behaviour from iodine, where the *flat* leak is what makes its headline
figure exact. Both are deliberate. The clamp is $[0, 150]$, so vitamin C can never go negative and a
surplus is bounded.

---

## 12. Glucose and insulin

### 12.1 The insulin index

$$n^*(g) = \begin{cases}
1, & g \le 5 \\[4pt]
1 + 2\,(g - 5), & 5 < g \le 8 \\[4pt]
7 + 4\,(g - 8), & g > 8
\end{cases}$$

Two ramps meeting without a step, which is what `ELEVATED_INSULIN_TARGET` exists to guarantee:

$$\texttt{ELEVATED\_INSULIN\_TARGET} = \underbrace{n_{\text{normal}}}_{1} + (\underbrace{8}_{g_{\text{elevated}}} - \underbrace{5}_{g_{\text{normal}}}) \times \underbrace{2}_{k_1} = 1 + 3 \times 2 = 7$$

so ramp $1$ reaches exactly $7$ at $g = 8$, which is where ramp $2$ starts. The continuity condition is
therefore

$$1 + k_1\,(g_e - g_0) = 7 = n^*(g_e^+),$$

and the second slope is **twice** the first - that is the meaning of "past $8$ the pancreas is
unmistakably responding". The index then approaches the target with `INSULIN_APPROACH` $= 0.0005$:

$$n' = \mathrm{clamp}\Big(n + (n^*(g) - n)\cdot 0.0005,\; 0,\; 60\Big)$$

a time constant of **$2\,000$ ticks** ($100$ seconds), so insulin lags a meal rather than tracking it
instantly.

### 12.2 Glucose

$$g' = \mathrm{clamp}\Big(g - \beta(g) - U - V,\; 0,\; 30\Big)$$

Three terms, and they are separate because they behave differently.

**The basal drain** $\beta$ is piecewise in three regions, and the shape is what makes glucose the one
quantity in the model that is *spent* rather than regulated:

$$\beta(g) = \begin{cases}
1.5\,y, & g > 5.5 \\[4pt]
y/3, & 3.5 < g \le 5.5 \\[4pt]
\dfrac{y}{3}\cdot\dfrac{g}{3.5}, & g \le 3.5
\end{cases}$$

Both live rates are written against a **yardstick** rather than as literals:

$$y = \frac{g_{\text{normal}} - g_{\text{floor}}}{2 \times 24\,000} = \frac{5 - 3.5}{48\,000} = 3.125\times10^{-5}
\quad(\texttt{GLUCOSE\_DECAY\_YARDSTICK})$$

That is the drain the model was *originally* calibrated around - $1.5$ mmol/L over two in-game days.
Nothing burns it at that rate any more; it is kept so the relationship between the two live rates
survives a recalibration:

$$\underbrace{\frac{y}{3} = 1.0417\times10^{-5}}_{\text{basal}}, \qquad
\underbrace{1.5\,y = 4.6875\times10^{-5}}_{\text{excess}}$$

The basal rate is $y/3$ (so the fall from $5.0$ to the $3.5$ floor now takes **six** days rather than
two) and the excess rate is $y \times 1.5$ - half again as fast as the model's *original* rate,
deliberately not half again as fast as the reduced one:

$$\frac{5.0 - 3.5}{y/3} = 144\,000 \text{ ticks} = 6 \text{ days}, \qquad
\frac{1.5}{1.5y} = 32\,000 \text{ ticks} \approx 1.33 \text{ days}.$$

That is what makes a spike cost something even though the normal band was made gentler. Below $3.5$ the
rate scales with the remaining glucose, so the fall decelerates instead of running straight to zero.

**Endogenous insulin uptake** is proportional to how far insulin is above fasting, and is **switched off
at the bottom of the reference range**:

$$U = \begin{cases}
0, & g \le 4 \\[4pt]
\min\big(10^{-4}\max(n - 1,\, 0),\; g - 4\big), & g > 4
\end{cases}$$

**Injected** insulin aspart is added with no brake at all:

$$V = 8\times10^{-5}\,\iota \qquad (\texttt{GLUCOSE\_UPTAKE\_PER\_INSULIN\_ASPART})$$

Two brakes on the endogenous term, and both matter. The $\min$ prevents an overshoot below $4.0$ in a
single tick, and the branch condition guarantees the pancreas never drives a body hypoglycaemic on its
own. **Injected insulin aspart has neither brake** - $V$ is added unconditionally - and that asymmetry
is the entire reason an overdose is dangerous.

### 12.3 Hypoglycaemia

$$\text{tier}(g) = \begin{cases}
3, & g < 1.3 \\[4pt]
2, & 1.3 \le g < 1.7 \\[4pt]
1, & 1.7 \le g < 2.0 \\[4pt]
0, & g \ge 2.0
\end{cases}
\qquad
D_{\text{hypo}}(g) = \begin{cases}
0, & g \ge 1.3 \\[4pt]
1.3 - g, & g < 1.3
\end{cases}$$

| Tier | Glucose | Effect |
| --- | --- | --- |
| 0 | $\ge 2.0$ | nothing |
| 1 | $< 2.0$ | mining fatigue |
| 2 | $< 1.7$ | and weakness |
| 3 | $< 1.3$ | and magic damage |

The damage scales with how far below the threshold the glucose is, so the last of it hurts most. It is
applied once per effect pass (§16.1) and is the one symptom in the model that can kill a player with no
monster involved other than sepsis.

---

## 13. Pharmacokinetics

`stepDrugs` runs first in the tick, before anything that reads a concentration. All compounds but
vitamin C use the **linear** form

$$d' = \max(d - r,\, 0), \qquad r = \frac{C}{T}$$

which means a drug's *lifetime* depends on how much of it there is: at the cap it is gone in the stated
time, and from half the cap in half the time.

| Compound | Cap $C$ | $T$ (ticks) | $r$ per tick | Form |
| --- | --- | --- | --- | --- |
| Salicin | 3 | 72,000 (3 days) | $4.167\times10^{-5}$ | linear |
| Dexamethasone | 2 | 48,000 (2 days) | $4.167\times10^{-5}$ | linear |
| Scopolamine | 5 | 24,000 (1 day) | $2.083\times10^{-4}$ | linear |
| Atropine | 5 | 24,000 (1 day) | $2.083\times10^{-4}$ | linear |
| Psilocybin | 10 | 12,000 (½ day) | $1.083\times10^{-4}$ | linear $\to$ psilocin |
| Psilocin | 10 | 24,000 (1 day) | $5.417\times10^{-5}$ | linear |
| Ephedrine | 5 | 24,000 (1 day) | $2.083\times10^{-4}$ | linear |
| Berberine | 7 | 60,000 (2.5 days) | $1.167\times10^{-4}$ | **scaled by CYP3A4** |
| Glycyrrhizin | 7 | 48,000 (2 days) | $1.458\times10^{-4}$ | linear |
| Naringin | 10 | 24,000 (1 day) | $4.167\times10^{-4}$ | linear |
| Ethanol | 1.0 | 24,000 (1 day) | $4.167\times10^{-5}$ | linear |
| Insulin aspart | 60 | 24,000 (1 day) | $2.5\times10^{-3}$ | linear |
| Pyrogen | $\pm 6$ | 24,000 (1 day) | $2.5\times10^{-4}$ | linear, gated (§8.3) |

Vitamin C (§11.2) is the exception and uses true first-order decay.

### 13.1 The psilocybin → psilocin chain

$$\kappa = \min\Big(\psi_{\text{by}},\; \frac{1.3}{12\,000}\Big), \qquad
\psi_{\text{by}}' = \psi_{\text{by}} - \kappa$$

$$\psi_{\text{cin}}' = \mathrm{clamp}\Big(\psi_{\text{cin}} + \kappa - \frac{1.3}{24\,000},\; 0,\; 10\Big)$$

A **one-for-one prodrug conversion**: what leaves psilocybin arrives as psilocin in the same tick, and
psilocin is cleared at the same time. The two rates are both written in units of one mushroom
(`PSILOCIN_DOSE` $= 1.3$), which is what makes the arithmetic readable:

- Psilocybin converts at $1.3/12\,000$ per tick, so a dose takes **half a game day** to convert.
- Psilocin clears at $1.3/24\,000$ per tick - a **flat** rate whatever the level, which is why a single
  mushroom is gone in a day but ten of them take eight. A proportional rate would make all ten vanish
  on the same schedule as one, which is not what a dose does.

Because psilocybin is the slower of the two, the psilocin level **rises for a while after eating** even
though none was swallowed: the prodrug keeps topping it up. That is what the $\min$ in $\kappa$ is for,
so the final partial tick converts only what is left.

### 13.2 Ethanol

`ETHANOL_CAP` $= 1.0$ and the cap is charged the full unit per drink, so the index is effectively
"fraction of one drink still in the body" cleared over one game day. It has no effect on any other
quantity in the model - it is tracked, not simulated.

---

## 14. CYP3A4 and the naringin interaction

The liver enzyme index is not a quantity with a life of its own. It is a **curve over naringin**,
recomputed every tick, so it can never disagree with the naringin by more than the one tick between
eating and the next step. The curve is defined by five knots $(\nu_i, \varepsilon_i)$:

$$(\nu_i, \varepsilon_i) = (0,\, 85),\; (2,\, 60),\; (4,\, 45),\; (7,\, 25),\; (8.5,\, 10)$$

| Naringin | CYP3A4 | Grapefruit slices |
| --- | --- | --- |
| 0 | 85 | none |
| 2 | 60 | 2 |
| 4 | 45 | 4 |
| 7 | 25 | 7 |
| $\ge 8.5$ | 10 | 8½ - **the floor** |

Between knots the curve is a **smoothstep**, not a linear interpolation. For $\nu_i \le \nu < \nu_{i+1}$:

$$t = \mathrm{clamp}\!\left(\frac{\nu - \nu_i}{\nu_{i+1} - \nu_i},\, 0,\, 1\right), \qquad
\varepsilon(\nu) = \varepsilon_i + (\varepsilon_{i+1} - \varepsilon_i)\,h(t), \qquad
h(t) = 3t^2 - 2t^3$$

and past the last knot, $\varepsilon = 10$ for all $\nu \ge 8.5$.

The smoothstep has **zero slope at both ends**:

$$h'(t) = 6t - 6t^2 = 6t(1 - t) \;\Longrightarrow\; h'(0) = h'(1) = 0,$$

so each knot is entered and left flat and the curve has no kink where two segments meet. A linear
interpolation would put a corner at every calibration point, and the enzyme index is read continuously
by the berberine metabolism, so those corners would be visible as a sudden change in how long a dose
lasts. One more slice always takes a little more off the liver instead of everything changing the
moment a threshold is crossed.

Past the last knot the activity **holds at $10$**, which is what makes eight and a half slices the
deepest the inhibition goes - $h$ is defined on a closed interval and the descent simply ends, so it is
the bottom of a descent rather than the far side of a jump.

The fraction actually used downstream is

$$\phi = \frac{\varepsilon}{85} \quad(\texttt{CYP3A4\_NORMAL}), \qquad
\phi\big|_{\varepsilon = 85} = 1.0, \qquad
\phi\big|_{\varepsilon = 10} = \frac{10}{85} \approx 0.1176$$

so the berberine metabolism is the one the model has always had when no grapefruit has been eaten. At
the floor

$$\text{lifetime factor} = \frac{1}{\phi} = \frac{85}{10} = 8.5\times$$

and berberine is the only compound scaled this way:

$$d_{\text{ber}}' = \max\big(d_{\text{ber}} - r_{\text{ber}}\,\phi,\; 0\big)$$

**Order within the tick:** naringin decays first, *then* the enzyme index is read off what is left. A
tick is the smallest step the model has, so "the liver clears berberine at the rate this much
grapefruit allows" is decided by the naringin the body actually still carries at the end of the tick.

---

## 15. Symptom magnitudes

These are read by the Kotlin layer, never written back into the state. Each is a pure function of the
body.

### 15.1 Severity - the shared normaliser

$$\mathrm{sev}(\lambda) = \mathrm{clamp}\!\left(\frac{\lambda}{60},\, 0,\, 1\right)$$

Saturating at `SEVERE_LOAD` $= 60$, so every load at or above $60$ is "as bad as it gets" for the
magnitudes below. Sepsis damage is the one that keeps scaling past it (§15.2).

### 15.2 Sepsis

$$D_{\text{sep}}(\lambda) = \begin{cases}
0, & \lambda < 60 \\[4pt]
1 + \dfrac{\lambda - 60}{40}, & \lambda \ge 60
\end{cases}$$

| Load | Damage per pass |
| --- | --- |
| 59 | 0 |
| 60 | 1.0 |
| 80 | 1.5 |
| 100 | 2.0 |

Half a point more for every $20$ points past the threshold. Applied every effect pass (§16.1), so a
load of $60$ costs **half a heart every two seconds** and $100$ a whole heart - ten hearts gone in
$40$ seconds and $20$ seconds respectively. Magic damage, so armour is no help: this is the pathogen,
not a blow.

### 15.3 Camera shake

Two independent quantities: **whether** there is a shake, and **how far** it swings.

$$\chi = \min\Big(\;\underbrace{0.2\,\mathbb{1}[\lambda \ge 8]}_{\text{symptomatic}}
+\underbrace{0.2\,\mathbb{1}[J \ge 75]}_{\text{storm}}
+0.1\,\mathbb{1}[w > 150]
+0.15\,\mathbb{1}[\mathrm{Mg} < 0.70]
+0.2\,\mathbb{1}[\mathrm{Ca} < 1.80]
+0.1\,\mathbb{1}[I > 1.20]
+\underbrace{0.15\,\lvert T_{\text{th}} \rvert}_{\text{thermal}},\;\; 0.85\Big)$$

| Source | Contribution | Why it is visible to the player |
| --- | --- | --- |
| Symptomatic ($\lambda \ge 8$) | $+0.2$ | the illness itself |
| Immune storm ($J \ge 75$) | $+0.2$ | a storm comes with a fever, which has an icon |
| Water $> 150$ | $+0.1$ | past the point the nausea icon appears |
| Magnesium $< 0.70$ | $+0.15$ | neuromuscular hyperexcitability |
| Calcium $< 1.80$ | $+0.2$ | |
| Iodine $> 1.20$ | $+0.1$ | thyrotoxic palpitations |
| Thermal stress | $+0.15 \times \lvert T_{\text{th}} \rvert$ | teeth chattering, rigors |

Capped at $0.85$, so no state shakes the camera every single roll. The design rule stated in the source
is that **every term must come from something the player can notice, and every source that can produce
a tremor also puts an effect icon on the screen** - otherwise the player has no way of telling why
their view is moving. That is why over-hydration only counts past the nausea threshold and why a fever
below $38.5$ counts for nothing.

The immune term is a **step, not a ramp**:

$$\mathbb{1}[J \ge 75] \quad\text{where}\quad \texttt{IMMUNE\_SHAKE\_ONSET} = \texttt{IMMUNE\_STORM\_THRESHOLD} = 75$$

Below the storm, inflammation contributes **nothing** to the shake chance however high it climbs; at the
storm it contributes the full $+0.2$ in one step. This is a deliberate change from an earlier version
that ramped in from $40$, and the reason is that the tremor is a storm symptom and nothing less - an
ordinary immune response, and the build-up to a storm, are not supposed to be visible as a tremor. It
also lines the shake *chance* up with the shake *amplitude* below, whose storm term was always gated
exactly here. The $+0.2$ is the old storm's doubling of the symptom term, kept as an **addition rather
than a multiplier**: a symptomatic body used to have its $0.2$ doubled to $0.4$ by a storm, and the
immune term now supplies that same $+0.2$ the moment the storm is reached. So a storm shakes exactly as
often as it always did, and a body below it never shakes from inflammation at all.

Note that with onset $=$ storm there is **no ground to ramp across** - the ramp's domain is
$[75, 75]$, of width zero - which is why the factor is a step and not a line.

The amplitude is a separate sum:

$$A = 0.3 + 0.4\,\mathrm{sev}(\lambda) + 0.3\,\mathbb{1}[J \ge 75]
+0.2\,\mathbb{1}[\mathrm{Ca} < 1.80] + 0.2\,\mathbb{1}[\text{thermal stress}]$$

| Condition | Amplitude |
| --- | --- |
| quiet body | $0.3°$ |
| severe load ($\ge 60$) | $0.7°$ |
| storm | $0.6°$ |
| cold or feverish | $0.5°$ |
| severe calcium deficit | $0.5°$ |
| **everything at once** | **$1.4°$** |

Deliberately gentle: the tremor is meant to read as being unwell rather than to make the game hard to
play, so even a severe infection with a storm and a fever behind it stays well inside the range a
player can aim through.

### 15.4 Food exhaustion and mining

$$M_{\text{exh}} = 1
+0.5\,\mathrm{sev}(\lambda)\,\mathbb{1}[\lambda \ge 8]
+0.5\,\mathbb{1}[J \ge 75]
+0.2\,\mathbb{1}[\text{overhydrated}]
+0.2\,\mathbb{1}[\mathrm{Cl} > 106]
+0.15\,\mathbb{1}[\mathrm{Mg} < 0.70]
+0.25\,\mathbb{1}[\text{thermal stress}]$$

$$P_{\text{mine}} = 0.06\,\mathrm{sev}(\lambda)\,\mathbb{1}[\lambda \ge 8]
+0.08\,\mathbb{1}[\text{overhydrated}]
+0.06\,\mathbb{1}[\mathrm{K} < 3.5]
+0.04\,\mathbb{1}[\mathrm{Ca} < 2.10]$$

Note the two are deliberately **not** scaled the same way. The exhaustion term is $0.5 \times
\mathrm{sev}$, so a mild illness costs little and a severe one the full half again; the mining penalty
is $0.06 \times \mathrm{sev}$ on the same shape. Everything else is a flat addition that does not care
how far outside the range the mineral is - being at $0.69$ magnesium is worth the same $0.15$ as being
at $0.2$, because the symptom is caused by being outside the band rather than by how far outside.

The exhaustion multiplier is a **multiplier around $1$** ("$1$ is nothing wrong"), so the terms compose
multiplicatively with vanilla's own food exhaustion. The mining penalty is a **positive fraction**
subtracted from mining speed. Two saturating helpers feed them elsewhere:

$$\text{thermalExhaustion}(T_{\text{th}}) = 0.03\,\lvert T_{\text{th}} \rvert, \qquad
\text{stormExhaustion} = 0.05$$

### 15.5 Tier functions

$$T_{\text{th}}(\Theta) = \begin{cases}
+2, & \Theta \ge 40.0 \\[4pt]
+1, & 38.5 \le \Theta < 40.0 \\[4pt]
0, & 36.0 < \Theta < 38.5 \\[4pt]
-1, & 35.0 < \Theta \le 36.0 \\[4pt]
-2, & \Theta \le 35.0
\end{cases}
\qquad
F(\Theta) = \begin{cases}
3, & \Theta \ge 40.0 \\[4pt]
2, & 39.5 \le \Theta < 40.0 \\[4pt]
1, & 38.5 \le \Theta < 39.5 \\[4pt]
0, & \Theta < 38.5
\end{cases}$$

$T_{\text{th}}$ is the **symptom** scale (signed, with two fever steps and two hypothermia steps) and
$F$ (`feverGrade`) is the **indicator** scale (unsigned, three fever steps). They are deliberately not
the same ladder: $39.5$ is worth telling the player about even though it makes the weakness no worse
than $38.6$ does. Both share the same three thresholds, so the indicator and the symptoms can never
disagree about where a fever begins or peaks. `hasPain` is separate again, keyed to
`PAIN_THRESHOLD` $= 39.5$ - pain arrives at the second grade and stays there, so a player at $40.5$ is
not told they are in three times the pain.

The tier functions are all **threshold ladders with no interpolation**, and the thresholds are shared
constants rather than repeated literals:

$$\text{hypo}(g): 3 \to 2 \to 1 \to 0 \text{ at } 1.3,\; 1.7,\; 2.0 \quad(\S12.3)$$

$$\text{psiTier}: 0 \to 1 \to 2 \to 3 \to 4 \text{ at } 1.2,\; 1.7,\; 2.5,\; 5.0$$

$$\text{thirstCells}(w) = \left\lfloor \mathrm{clamp}\!\left(\frac{w}{10},\, 0,\, 10\right) \right\rfloor$$

$\text{psiTier}$'s stages are **nested** - a stage $3$ trip still has the outlines and the colour,
because the client effect for it is the whole thing at a higher intensity rather than a layer on top.
The thresholds are strictly increasing ($1.2 < 1.7 < 2.5 < 5.0$) and the comparisons are strict $>$:

$$\text{psiTier} = \#\{i : \psi > \theta_i\}, \qquad
\theta = (1.2,\, 1.7,\, 2.5,\, 5.0),$$

so the tier is exactly $0$ at a level of $1.2$ and $1$ just above it - a body at the outline threshold
has no outlines yet.

$\text{thirstCells}$ divides the $0..200$ water index into **ten cells of $10$**, which is what the
thirst bar draws; $100$ water is therefore $10$ full cells, and the scale is the same one `/aliment`
reports.

The environment constant `TEMPERATE_BIOME` $= 0.8$ is vanilla's idea of a temperate biome, so a biome
at $0.8$ is the neutral point of the ambient scale.

---

## 16. Order of operations in one tick

`Physiology.tick` runs the steps in a specific order, and the order is not arbitrary - each choice is
recorded here because changing it changes the model.

| # | Step | Reads |
| --- | --- | --- |
| 1 | `stepDrugs` | drugs decay; naringin decays, then CYP3A4 is read off what is left |
| 1 | `enzymes` | $= \varepsilon(\text{naringin})$ - the value the metabolism just used |
| 1 | `stepPyrogen` | pyrogen clears, gated on the settled tolerance |
| 1b | `stepInsulin` | the body's own insulin moves towards what the glucose asks |
| 1b | `stepGlucose` | the **new** insulin and the **new** insulin aspart |
| 1b | `stepBacteria` / `stepVirus` | the **old** competence |
| 2 | `immuneActive` | latches on above the activation load (hysteresis) |
| 3 | `stepMediators` | the five mediators approach their targets; uses the **old** inflammation for $K$, the **new** load for $S$ |
| 4 | `stepTemperature` | reads the **new** mediators; writes temperature once |
| 4 | `stepWater` | the temperature the tick **started** at |
| 4 | `stepElectrolytes` | the new water (for flush) and the new temperature |
| 4 | `stepTraceElements` | the same |

Four of these deserve to be called out.

**Drugs first.** Everything downstream that reads a concentration must see the current one, so
metabolism leads. This also means a dose taken in a tick is felt immediately but has already lost one
tick's worth of decay.

**Enzymes are re-read from the post-decay naringin**, and then written into the state as well as used
for the berberine step. So the state's $\varepsilon$ and the rate that was applied agree **by
construction** rather than by two call sites happening to ask the same question.

**Glucose reads the new insulin but the old competence.** Insulin is updated in the same phase because
it is part of the same subsystem; the pathogen/immune step deliberately reads the competence computed
from the state at the start of the tick, which is what keeps the immune response a step behind the
infection rather than instantaneous.

**Temperature is written once, and the water steps read the old value.** The comment in the source is
explicit: everything the body *does* about a temperature - sweating water and salt away - is driven by
the temperature it started the tick at, so each step reads the previous state and the new temperature
is written once at the end. Reading the new temperature would let a fever sweat against a temperature
the body had not yet reached.

### 16.1 From ticks to effects: the Kotlin cadence

Everything above is per tick. The **symptom magnitudes of §15 are per *effect pass***, and a pass is not
a tick - this is the one place where a figure from the model meets a number that lives in the Kotlin
glue, so it is worth stating exactly.

`AlimentSymptoms.tick` walks every online player once per server tick to advance the body, but it only
*applies effects* when the shared cooldown is a multiple of the interval:

$$c \bmod 40 = 0 \qquad (\texttt{EFFECT\_INTERVAL\_TICKS} = 40)$$

Each effect is then applied for $40 \times 3 = 120$ ticks. That separation is deliberate: an effect that
stops being warranted simply **lapses** after three intervals instead of having to be tracked and
removed. The model never sees either number - it is asked "what is the damage" and answers per pass.

**The counter is shared with the camera shake, so the cadence is not a clean $40$ ticks.** The same
`shakeCooldown` field $c$ is read by the effect pass and written by `rollShake`, which counts down from
`SHAKE_INTERVAL_TICKS` $= 300$ and resets when it reaches zero:

$$c \leftarrow \begin{cases}
c - 1, & c > 0 \\[4pt]
300, & c = 0
\end{cases}$$

The counter therefore runs $300 \to 0$ over **$301$ ticks** and restarts, and a pass happens on every
tick whose counter value is a multiple of $40$. Those values are $0, 40, \ldots, 280$ - **eight** of
them, because $300$ is *not* a multiple of $40$:

$$\left\lfloor \frac{300}{40} \right\rfloor + 1 = 7 + 1 = 8, \qquad 300 = 7 \times 40 + 20 .$$

So the true cadence is

$$\frac{301 \text{ ticks per cycle}}{8 \text{ passes per cycle}} = 37.625 \text{ ticks per pass}$$

against the $40$ the KDoc states. The pattern within a cycle is irregular - seven gaps of $40$ ticks
followed by one of $21$, when the shake's reset lands just past a multiple of $40$ - and the long-run
average is

$$\frac{40}{37.625} - 1 \approx 6.3\%$$

faster than a clean $40$-tick pass. That is an emergent property of sharing the counter rather than a
figure anyone chose, and it is worth knowing before quoting a per-second rate.

Taking $37.625$ ticks per pass gives a pass rate of

$$\frac{20 \text{ ticks/s}}{37.625 \text{ ticks/pass}} \approx 0.5316 \text{ passes/second},$$

so a model figure converts to what the player experiences as follows:

| Model figure | Per pass | Per second | In-game outcome |
| --- | --- | --- | --- |
| $D_{\text{sep}} = 1.0$ (load 60) | 1.0 | 0.5316 | ten hearts in ~37.6 s |
| $D_{\text{sep}} = 1.5$ (load 80) | 1.5 | 0.7974 | ten hearts in ~25.1 s |
| $D_{\text{sep}} = 2.0$ (load 100) | 2.0 | 1.0632 | ten hearts in ~18.8 s |
| $D_{\text{hypo}}$ at glucose 1.0 | 0.3 | 0.1595 | |

A Minecraft health bar is $20$ points. The rounded "half a heart every two seconds" in the source's own
KDoc describes an idealised $40$-tick pass and is about $6\%$ optimistic in the player's favour.

**The shake has its own interval.** `rollShake` rolls $\chi$ on the tick the counter reaches zero, which
is once per **$301$ ticks ($15.05$ seconds)** - so the chance in §15.3 is per $15$-second roll, not per
pass and not per tick, and $\chi = 0.2$ means a tremor about once every $75$ seconds.

---

## 17. Time constants at a glance

Every "how long does X take" in the model, with the mechanism it comes from.

| Quantity | Constant | Ticks | In-game time | Mechanism |
| --- | --- | --- | --- | --- |
| Mediators | `MEDIATOR_APPROACH` $= 0.002$ | 500 | 25 s | `approach` time constant $1/k$ |
| Insulin | `INSULIN_APPROACH` $= 0.0005$ | 2,000 | 100 s | `approach` time constant |
| Temperature | `TEMPERATURE_APPROACH` $= 0.0004$ | 2,500 | ~2 min | `approach` time constant |
| Electrolytes | `ELECTROLYTE_HOMEOSTASIS` $= 0.00005$ | 20,000 | < 1 day | `approach` time constant |
| Water (idle) | $100/(5 \cdot 24\,000)$ | 120,000 | **5 days** | flat drain, $100 \to 0$ |
| Water at 39 °C | sweat parabola | 84,000 | **3.5 days** | calibrated |
| Water at 40 °C | sweat parabola | 48,000 | **2 days** | calibrated |
| Pathogen clearance | $20/(2 \cdot 24\,000)$ | 48,000 | **2 days** | clears a load of 20 at full competence |
| Drug-suppressed clearance | $100/(1.5 \cdot 24\,000)$ | 36,000 | **1.5 days** | $100 \to 0$ at threshold |
| Iodine | flat leak | 360,000 | **15 days** | $0.50 \to 0.05$, exact |
| Vitamin C | $\ln 2/(5 \cdot 24\,000)$ | 120,000 | **5 days** | **half-life** ($80 \to 40$) |
| Glucose (normal band) | $y/3$ | 144,000 | **6 days** | 1.5 mmol/L from 5.0 |
| Glucose (over range) | $y \times 1.5$ | 32,000 | ~1.3 days | 1.5 mmol/L at the faster rate |
| Pyrogen | $6/24\,000$ | 24,000 | **1 day** | linear, from cap |
| Salicin | $3/72\,000$ | 72,000 | **3 days** | linear, from cap |
| Dexamethasone | $2/48\,000$ | 48,000 | **2 days** | linear, from cap |
| Berberine | $7/60\,000$ | 60,000 | **2.5 days** | linear, $\div\, \phi$ |
| Glycyrrhizin | $7/48\,000$ | 48,000 | **2 days** | linear |
| Naringin | $10/24\,000$ | 24,000 | **1 day** | linear |
| Psilocybin $\to$ psilocin | $1.3/12\,000$ | 12,000 | **½ day** | linear, one-for-one |
| Psilocin | $1.3/24\,000$ | 24,000 | **1 day** | linear, from one dose |
| Scopolamine / atropine / ephedrine | $5/24\,000$ | 24,000 | **1 day** | linear |
| Ethanol | $1/24\,000$ | 24,000 | **1 day** | linear, from cap |
| Insulin aspart | $60/24\,000$ | 24,000 | **1 day** | linear, from cap |

---

## 18. Numerical notes

**Clamps are everywhere and they are load-bearing.** Almost every step ends in a $\mathrm{clamp}$ or a
$\max(\cdot, 0)$. This is not defensive padding: the model has no stability proof, and several terms
(the sweat parabola below its dead zone, the endogenous insulin uptake, drug decay) would go negative
or overshoot without them. A change that removes a clamp should be treated as a change to the dynamics,
not a cleanup.

**Euler is safe here, comfortably.** The fastest quantity in the model is pyrogen and insulin aspart,
both decaying at $2.5\times10^{-3}$ per tick, and the fastest state-dependent rate is psilocybin
conversion at $1.083\times10^{-4}$. Explicit Euler is stable for a decay of the form
$x \leftarrow x(1-k)$ whenever $k < 1$:

$$1 - k \in (-1, 1) \;\Longleftrightarrow\; 0 < k < 2,$$

so the model is more than two orders of magnitude inside the limit. The three `approach` constants
($0.002$, $0.0005$, $0.0004$) are all far below $1$, so none of them can overshoot.

**Everything is single-precision.** The model is `Float` throughout, which is what the Minecraft side
stores. Accumulated drift over a long simulation is real but bounded by the clamps, and the self tests
assert *ranges* and *directions* rather than long-run exact values wherever drift could matter - the
assertions that pin exact figures (the five-day water drain, the fifteen-day iodine drain,
$80 \to 40$ in five days) are all short enough that float error cannot reach their tolerance.

**Only one quantity is a ratio rather than a level**, and that is $\phi = \varepsilon/85$. It is the
form a clearance rate actually wants, so the division happens once rather than at each call site - and
a rate that reads it cannot accidentally forget to.

**Two deliberate discontinuities exist**, both documented at their site:

1. The $S$ cliff at $\lambda = 20$ when immunity is already engaged (§6.1).
2. Every threshold that is written as a step rather than a ramp - the two drug fevers (§8.1), the
   three CYP3A4 plateaus past the last knot (§14), and the immune shake factor (§15.3).

The steps are deliberate choices about what the model *means* rather than approximations of something
smoother, which is why they are commented as such at each site.

---

## 19. Where to change what

| To change | Edit | Watch out for |
| --- | --- | --- |
| A threshold or a rate | `ModelConstants` in `Model.scala` | a figure quoted in the docs and the self test |
| A curve's shape | `Physiology.scala` | the local `private final val`s are part of the model, not tuning knobs |
| A reference range | `MineralRanges` | $\rho$ divides by $x_n$, so changing $x_n$ moves every ranking |
| The mediator weights | `MediatorLevels` | they must sum to $1$ and the resting set must reproduce $J = 25$ |
| How strong a food is | `AlimentIngestion` (Kotlin) | that is the food table, not the model; the model only says what a "plant food serving" is worth |

**The number belongs in Scala.** If a value is needed on both sides of the seam it goes into the model
and is re-exported through `AlimentModelBridge`, which must contain no numbers of its own. The self
tests live in `AlimentPhysiologySelfTest` and are the specification of record for everything above;
see [tools/README.md](../tools/README.md) for how to run them.
