package metrics

import (
	"os/exec"
	"runtime"
	"strings"
	"testing"
)

// processScript returns the PS and PSMETA part of collectScript as a runnable
// shell script, so the real snippet can be checked against the local ps.
func processScript(t *testing.T) string {
	t.Helper()
	body := strings.TrimSuffix(strings.TrimPrefix(collectScript, "sh -c '"), "'")
	start := strings.Index(body, `printf "__SUI_PS__\n"`)
	end := strings.Index(body, `printf "__SUI_VIRT__\n"`)
	if start < 0 || end < start {
		t.Fatal("process section not found in collectScript")
	}
	return body[start:end]
}

func TestProcessScriptSortsByCPUAndMatchesMeta(t *testing.T) {
	if _, err := exec.LookPath("sh"); err != nil {
		t.Skip("sh not available")
	}
	out, err := exec.Command("sh", "-c", processScript(t)).Output()
	if err != nil {
		t.Fatalf("run process script: %v", err)
	}
	sections := SplitSections(string(out))
	procs := ParseProcesses(sections["PS"])
	if len(procs) == 0 {
		t.Fatalf("no processes parsed from %q", sections["PS"])
	}

	// Metadata must describe the same processes that were listed.
	var metaPIDs []string
	for _, line := range strings.Split(sections["PSMETA"], "\n") {
		if fields := strings.Fields(line); len(fields) > 0 {
			metaPIDs = append(metaPIDs, fields[0])
		}
	}
	var psPIDs []string
	for _, line := range strings.Split(strings.TrimSpace(sections["PS"]), "\n") {
		if fields := strings.Fields(line); len(fields) > 0 {
			psPIDs = append(psPIDs, fields[0])
		}
	}
	if strings.Join(metaPIDs, ",") != strings.Join(psPIDs, ",") {
		t.Fatalf("PSMETA pids %v do not match PS pids %v", metaPIDs, psPIDs)
	}

	// procps on Linux supports --sort, so the list must start with the busiest.
	if runtime.GOOS == "linux" {
		for i := 1; i < len(procs); i++ {
			if procs[i].CPU > procs[i-1].CPU {
				t.Fatalf("processes not sorted by CPU at %d: %+v", i, procs)
			}
		}
	}
}
