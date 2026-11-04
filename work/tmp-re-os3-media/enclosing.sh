#!/bin/bash
# print enclosing class descriptor + processing header for given line numbers
F="${2:-/g/os3-ref/os3-dexdump-f.txt}"
for L in $1; do
  awk -v target="$L" 'NR<=target { if ($0 ~ /^Class descriptor|^  Class descriptor/) {cls=$0; cln=NR} if ($0 ~ /^Processing/) {proc=$0} } NR==target {gsub(/^ +/,"",cls); print target": "cls" (line "cln") | dex: "proc; exit}' "$F"
done
