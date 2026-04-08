# ConflictBench

A benchmark dataset and evaluation framework for **textual merge conflicts** in Java projects. This repository contains the dataset, evaluation scripts, merge tools, and an LLM-based evaluation experiment built on the same benchmark.

📄 **Paper:** Shen, B., and Na Meng. "ConflictBench: A benchmark to evaluate software merge tools." *Journal of Systems and Software*, 214, 2024. [[PDF]](https://par.nsf.gov/servlets/purl/10515800)

---

## Overview

When developers merge software branches, **textual conflicts** occur when edits from different branches modify the same lines in divergent ways. While many automated merge tools exist, there is no standardized benchmark to fairly compare their capabilities.

**ConflictBench** addresses this gap by providing:
- **180 manually curated merge conflict scenarios** sampled from 208 popular open-source Java repositories
- **Ground-truth developer resolutions** for each scenario, obtained by locating the developer's actual merged version
- A **reproducible evaluation pipeline** that runs 5 state-of-the-art merge tools against the dataset and scores them across 3 dimensions

### Evaluation Dimensions

| Dimension | Description |
|-----------|-------------|
| **Tool Applicability** | Can the tool process this scenario? (e.g., some tools only handle Java files) |
| **Detection Precision** | When a tool reports a conflict as unresolvable, is it truly unresolvable? |
| **Resolution Desirability** | When a tool produces a resolution, does it match the developer's version semantically? |

### Key Findings

- No single tool dominates across all conflict scenarios
- Tool performance varies significantly by project domain and conflict characteristics
- LLMs outperform traditional merge tools in the majority of scenarios, but introduce new failure modes including hallucinated resolutions and syntactically invalid outputs (see [LLM Experiment](#llm-experiment))

---

## Repository Structure

```
ConflictBench/
├── 20+_Conflicts/              # Pioneer experiment: conflicts with 20+ conflicting lines
│   ├── ExoPlayer/<commit>/
│   ├── SimianArmy/<commit>/
│   └── ... (10 projects total)
├── Data/
│   ├── ConflictBench.xlsx      # Complete dataset: 180 scenarios with
│   │                           # true/false conflict labels and developer resolutions
│   │                           # with tool's resolution and manually crafted labels
│   └── total_list.txt          # Full list of candidate merge scenarios
├── LLM_Experiment/             # LLM-based evaluation (see section below)
│   ├── Data/                   # LLM inputs, outputs, and manually verified results
│   ├── Logger/                 # Execution logs
│   └── Script/                 # LLM evaluation scripts
├── MergeTools/                 # 5 state-of-the-art merge tools (shared across experiments)
│   ├── AutoMerge/
│   ├── FSTMerge/
│   ├── IntelliMerge/
│   ├── JDime/
│   └── KDiff3/
├── Resource/
│   └── merge_scenarios/        # 180 scenario folders (one per conflict)
├── Script/
│   └── script.py               # Main evaluation script
├── requirements.txt
└── README.md
```

### Scenario Folder Structure

Each folder in `Resource/merge_scenarios/` is named after the source project and contains one subfolder named with the **commit hash** of the developer's merged version:

```
<project_name>/
└── <commit_hash>/          # Developer's version
    ├── base/               # Common ancestor version
    ├── left/               # Left branch version
    ├── right/              # Right branch version
    ├── child/              # Developer's version (ground truth)
    ├── FSTMerge/           # FSTMerge tool output
    ├── JDime/              # JDime tool output
    ├── IntelliMerge/       # IntelliMerge tool output
    └── AutoMerge/          # AutoMerge tool output
```

> Only the conflicting files are retained in each folder. All other project files are removed to keep the repository lightweight.

---

## Quick Start

### Prerequisites

```bash
pip install -r requirements.txt
apt install libgit2-1.1   # Required for JDime and AutoMerge
```

### Running the Experiment

1. Clone the repository:
   ```bash
   git clone https://github.com/UBOWENVT/ConflictBench.git
   cd ConflictBench
   ```

2. Update the path prefix in `Script/script.py`:
   ```python
   path_prefix = "/your/local/path/to/ConflictBench"
   ```

3. Run the evaluation:
   ```bash
   python Script/script.py
   ```

**Notes:**
- `resume_experiment` is set to `False` by default. Set it to `True` to resume from a previous run using the stored `project_record.txt`.
- `project_record.txt` is updated incrementally as the script runs. Results are written to `Resource/output/`.

---

## LLM Experiment

The `LLM_Experiment/` folder contains a follow-up study that evaluates **large language models** on the same 180-scenario benchmark, using the same evaluation dimensions and ground-truth resolutions from `Data/ConflictBench.xlsx`.

### Models Evaluated
- GPT-4o-mini (OpenAI)
- Gemini 2.0 Flash (Google)

### Contents

| Folder | Description |
|--------|-------------|
| `LLM_Experiment/Script/` | Scripts for prompt construction, API calls, and batch evaluation |
| `LLM_Experiment/Data/` | LLM inputs/outputs and manually verified evaluation results |
| `LLM_Experiment/Logger/` | Execution logs |

> The 5 merge tools in `MergeTools/` are shared between the main experiment and the LLM experiment.

---

## Dataset Summary

| Item | Count |
|------|-------|
| Source repositories mined | 208 |
| Merge scenarios extracted | 117,218 |
| Scenarios in benchmark (manually curated) | 180 |
| Merge tools evaluated | 5 |
| LLM models evaluated | 2 |

---

## Citation

If you use ConflictBench in your research, please cite:

```bibtex
@article{shen2024conflictbench,
  title     = {ConflictBench: A benchmark to evaluate software merge tools},
  author    = {Shen, Bowen and Meng, Na},
  journal   = {Journal of Systems and Software},
  volume    = {214},
  year      = {2024}
}
```

---

## Related Work

This benchmark was developed as part of a broader empirical study on merge conflicts:

> Shen, B., Gulzar, M. A., He, F., and Meng, N. "A characterization study of merge conflicts in Java projects." *ACM Transactions on Software Engineering and Methodology*, 32(2), 2023.
