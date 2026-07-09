#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PDF_DIR="$ROOT_DIR/testdata/pdf"
BASELINE_ROOT="$PDF_DIR/golden"
OUTPUT_ROOT="$ROOT_DIR/build/pdf-golden"
DPI="${PDF_GOLDEN_DPI:-96}"
FUZZ="${PDF_GOLDEN_FUZZ:-1%}"
TOLERANCE_PIXELS="${PDF_GOLDEN_TOLERANCE_PIXELS:-25}"

if [[ "${1:-}" == "--record" ]]; then
    MODE="record"
else
    MODE="verify"
fi

for tool in pdftoppm pdfinfo compare; do
    if ! command -v "$tool" >/dev/null 2>&1; then
        echo "Missing required PDF golden tool: $tool" >&2
        echo "Install poppler-utils and ImageMagick, then rerun this script." >&2
        exit 2
    fi
done

shopt -s nullglob
pdfs=("$PDF_DIR"/*.pdf)
if [[ ${#pdfs[@]} -eq 0 ]]; then
    echo "No PDF fixtures found in $PDF_DIR" >&2
    exit 1
fi

rm -rf "$OUTPUT_ROOT"
mkdir -p "$OUTPUT_ROOT"

failed=0
for pdf in "${pdfs[@]}"; do
    name="$(basename "$pdf" .pdf)"
    actual_dir="$OUTPUT_ROOT/$name"
    baseline_dir="$BASELINE_ROOT/$name"
    mkdir -p "$actual_dir"

    echo "==> Rendering $name at ${DPI} DPI"
    pdftoppm -r "$DPI" -png "$pdf" "$actual_dir/page"

    if [[ "$MODE" == "record" ]]; then
        rm -rf "$baseline_dir"
        mkdir -p "$baseline_dir"
        cp "$actual_dir"/page-*.png "$baseline_dir"/
        echo "Recorded PDF golden baseline: $baseline_dir"
        continue
    fi

    mapfile -t actual_pages < <(find "$actual_dir" -maxdepth 1 -name 'page-*.png' | sort)
    mapfile -t baseline_pages < <(find "$baseline_dir" -maxdepth 1 -name 'page-*.png' | sort)

    expected_pages="$(pdfinfo "$pdf" | awk -F: '/^Pages:/ { gsub(/^[ \t]+/, "", $2); print $2 }')"
    if [[ "${#actual_pages[@]}" != "$expected_pages" ]]; then
        echo "Page count mismatch for $name: pdfinfo=$expected_pages rendered=${#actual_pages[@]}" >&2
        failed=1
    fi
    if [[ "${#actual_pages[@]}" != "${#baseline_pages[@]}" ]]; then
        echo "Golden page count mismatch for $name: baseline=${#baseline_pages[@]} rendered=${#actual_pages[@]}" >&2
        failed=1
        continue
    fi

    for actual in "${actual_pages[@]}"; do
        page="$(basename "$actual")"
        baseline="$baseline_dir/$page"
        diff="$actual_dir/${page%.png}-diff.png"
        if [[ ! -f "$baseline" ]]; then
            echo "Missing golden page for $name: $baseline" >&2
            failed=1
            continue
        fi

        status=0
        diff_pixels="$(compare -metric AE -fuzz "$FUZZ" "$baseline" "$actual" "$diff" 2>&1)" || status=$?
        diff_pixels="${diff_pixels:-0}"
        if [[ "$status" -gt 1 ]]; then
            echo "Image comparison failed for $name/$page: $diff_pixels" >&2
            failed=1
        elif [[ "$diff_pixels" =~ ^[0-9]+$ && "$diff_pixels" -gt "$TOLERANCE_PIXELS" ]]; then
            echo "PDF golden mismatch for $name/$page: $diff_pixels pixels differ; diff: $diff" >&2
            failed=1
        else
            rm -f "$diff"
        fi
    done
done

if [[ "$failed" -ne 0 ]]; then
    echo "PDF golden verification failed. Review artifacts under $OUTPUT_ROOT" >&2
    exit 1
fi

echo "PDF golden verification passed."
