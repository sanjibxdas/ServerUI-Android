package servers

import (
	"bytes"
	"context"
	"encoding/base64"
	"image"
	"image/png"
	"os"
	"path/filepath"
	"testing"
)

func TestPresetIDs(t *testing.T) {
	if len(presetIcons) != 16 {
		t.Fatalf("presets %d", len(presetIcons))
	}
	if _, ok := presetIcons[DefaultIconID]; !ok {
		t.Fatal("missing default")
	}
	if _, err := normalizeIconID(""); err != nil || mustIcon("") != DefaultIconID {
		t.Fatalf("empty id: %v", err)
	}
	if _, err := normalizeIconID("nope"); err == nil {
		t.Fatal("expected invalid icon")
	}
}

func mustIcon(id string) string {
	got, err := normalizeIconID(id)
	if err != nil {
		return ""
	}
	return got
}

func TestCustomIconRoundTripAndRejection(t *testing.T) {
	svc := testService(t)
	dir := t.TempDir()
	svc.SetIconDir(dir)
	ctx := context.Background()
	pngURL := tinyPNGDataURL(t)

	created, err := svc.Create(ctx, Input{
		Name: "Lab", Host: "203.0.113.10", Port: 22, Username: "deploy",
		AuthType: AuthPassword, Password: "secret",
		IconID:    "database",
		IconImage: &pngURL,
	})
	if err != nil {
		t.Fatal(err)
	}
	if created.IconID != "database" || !created.CustomIcon {
		t.Fatalf("icon %+v", created)
	}
	raw, contentType, err := svc.OpenIcon(ctx, created.ID)
	if err != nil || contentType != "image/png" || len(raw) == 0 {
		t.Fatalf("read %v %s %d", err, contentType, len(raw))
	}
	if _, err := os.Stat(filepath.Join(dir, created.ID+".png")); err != nil {
		t.Fatal(err)
	}

	bad := "data:image/svg+xml;base64,PHN2Zy8+"
	if _, err := svc.Update(ctx, created.ID, Input{
		Name: "Lab", Host: "203.0.113.10", Port: 22, Username: "deploy",
		AuthType: AuthPassword, IconID: "database", IconImage: &bad,
	}); err == nil {
		t.Fatal("expected svg rejection")
	}

	cleared := ""
	updated, err := svc.Update(ctx, created.ID, Input{
		Name: "Lab", Host: "203.0.113.10", Port: 22, Username: "deploy",
		AuthType: AuthPassword, IconID: "backup", IconImage: &cleared,
	})
	if err != nil {
		t.Fatal(err)
	}
	if updated.CustomIcon || updated.IconID != "backup" {
		t.Fatalf("cleared %+v", updated)
	}
	if _, _, err := svc.OpenIcon(ctx, created.ID); err == nil {
		t.Fatal("expected missing icon file")
	}
}

func TestCustomIconRejectsOversizedAndInvalidBytes(t *testing.T) {
	svc := testService(t)
	svc.SetIconDir(t.TempDir())
	ctx := context.Background()
	huge := "data:image/png;base64," + base64.StdEncoding.EncodeToString(bytes.Repeat([]byte("a"), maxIconBytes+1))
	if _, err := svc.Create(ctx, Input{
		Name: "Lab", Host: "203.0.113.10", Port: 22, Username: "deploy",
		AuthType: AuthPassword, Password: "secret", IconImage: &huge,
	}); err == nil {
		t.Fatal("expected oversized rejection")
	}

	var buf bytes.Buffer
	if err := png.Encode(&buf, image.NewNRGBA(image.Rect(0, 0, maxIconEdge+1, 2))); err != nil {
		t.Fatal(err)
	}
	wide := "data:image/png;base64," + base64.StdEncoding.EncodeToString(buf.Bytes())
	if _, err := svc.Create(ctx, Input{
		Name: "Lab", Host: "203.0.113.10", Port: 22, Username: "deploy",
		AuthType: AuthPassword, Password: "secret", IconImage: &wide,
	}); err == nil {
		t.Fatal("expected dimension rejection")
	}

	junk := "data:image/png;base64," + base64.StdEncoding.EncodeToString([]byte("not-an-image"))
	if _, err := svc.Create(ctx, Input{
		Name: "Lab", Host: "203.0.113.10", Port: 22, Username: "deploy",
		AuthType: AuthPassword, Password: "secret", IconImage: &junk,
	}); err == nil {
		t.Fatal("expected content rejection")
	}
}

func TestReadIconRejectsPathEscape(t *testing.T) {
	svc := testService(t)
	svc.SetIconDir(t.TempDir())
	if _, _, err := svc.ReadIcon("abcdef", "../secret.png"); err == nil {
		t.Fatal("expected rejection")
	}
}

func tinyPNGDataURL(t *testing.T) string {
	t.Helper()
	var buf bytes.Buffer
	if err := png.Encode(&buf, image.NewNRGBA(image.Rect(0, 0, 8, 8))); err != nil {
		t.Fatal(err)
	}
	return "data:image/png;base64," + base64.StdEncoding.EncodeToString(buf.Bytes())
}
