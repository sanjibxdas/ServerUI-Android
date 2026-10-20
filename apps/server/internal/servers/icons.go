package servers

import (
	"bytes"
	"encoding/base64"
	"fmt"
	"image"
	"image/jpeg"
	"image/png"
	"os"
	"path/filepath"
	"strings"

	"golang.org/x/image/webp"
)

// Preset icon ids. Keep in sync with apps/web/src/lib/server-icons.ts.
const DefaultIconID = "server"

var presetIcons = map[string]struct{}{
	"server":      {},
	"cloud":       {},
	"homelab":     {},
	"production":  {},
	"development": {},
	"staging":     {},
	"ai-gpu":      {},
	"database":    {},
	"storage":     {},
	"network":     {},
	"web":         {},
	"containers":  {},
	"security":    {},
	"backup":      {},
	"desktop":     {},
	"custom":      {},
}

const (
	maxIconBytes = 512 << 10
	maxIconEdge  = 512
)

func iconIDOrDefault(id string) string {
	if _, ok := presetIcons[id]; ok {
		return id
	}
	return DefaultIconID
}

// IconDirectory is where custom server images are written. It is not served as static files.
func IconDirectory(sqlitePath string) string {
	if dir := strings.TrimSpace(os.Getenv("SERVERUI_ICON_DIR")); dir != "" {
		return dir
	}
	if strings.TrimSpace(sqlitePath) != "" {
		return filepath.Join(filepath.Dir(sqlitePath), "server-icons")
	}
	if dir := strings.TrimSpace(os.Getenv("SERVERUI_DATA_DIR")); dir != "" {
		return filepath.Join(dir, "server-icons")
	}
	return filepath.Join("data", "server-icons")
}

func normalizeIconID(id string) (string, error) {
	id = strings.TrimSpace(id)
	if id == "" {
		return DefaultIconID, nil
	}
	if _, ok := presetIcons[id]; !ok {
		return "", fmt.Errorf("invalid server icon")
	}
	return id, nil
}

func (s *Service) saveIcon(serverID, dataURL string) (string, error) {
	if strings.TrimSpace(s.iconDir) == "" {
		return "", fmt.Errorf("unable to store server icon")
	}
	if !safeServerID(serverID) {
		return "", fmt.Errorf("invalid server")
	}
	raw, err := decodeIconPayload(dataURL)
	if err != nil {
		return "", err
	}
	ext, err := extForIcon(raw)
	if err != nil {
		return "", err
	}
	if err := os.MkdirAll(s.iconDir, 0o750); err != nil {
		return "", fmt.Errorf("unable to store server icon")
	}
	s.removeIconFiles(serverID)
	name := serverID + ext
	path := filepath.Join(s.iconDir, name)
	if err := os.WriteFile(path, raw, 0o640); err != nil {
		return "", fmt.Errorf("unable to store server icon")
	}
	return name, nil
}

func (s *Service) removeIconFiles(serverID string) {
	if !safeServerID(serverID) || s.iconDir == "" {
		return
	}
	for _, ext := range []string{".png", ".jpg", ".webp"} {
		_ = os.Remove(filepath.Join(s.iconDir, serverID+ext))
	}
}

func (s *Service) ReadIcon(serverID, storedName string) ([]byte, string, error) {
	if !safeServerID(serverID) || !safeIconFile(serverID, storedName) || s.iconDir == "" {
		return nil, "", fmt.Errorf("server icon not found")
	}
	path := filepath.Join(s.iconDir, storedName)
	raw, err := os.ReadFile(path)
	if err != nil {
		return nil, "", fmt.Errorf("server icon not found")
	}
	ext, err := extForIcon(raw)
	if err != nil {
		return nil, "", fmt.Errorf("server icon not found")
	}
	switch ext {
	case ".png":
		return raw, "image/png", nil
	case ".jpg":
		return raw, "image/jpeg", nil
	default:
		return raw, "image/webp", nil
	}
}

func safeServerID(id string) bool {
	if id == "" || len(id) > 64 {
		return false
	}
	for _, r := range id {
		if (r < '0' || r > '9') && (r < 'a' || r > 'f') {
			return false
		}
	}
	return true
}

func safeIconFile(serverID, name string) bool {
	if name != serverID+".png" && name != serverID+".jpg" && name != serverID+".webp" {
		return false
	}
	return !strings.Contains(name, "..") && filepath.Base(name) == name
}

func decodeIconPayload(dataURL string) ([]byte, error) {
	value := strings.TrimSpace(dataURL)
	comma := strings.IndexByte(value, ',')
	if comma < 0 || !strings.HasPrefix(strings.ToLower(value[:comma]), "data:image/") {
		return nil, fmt.Errorf("server icon must be a PNG, JPEG, or WebP image")
	}
	raw, err := base64.StdEncoding.DecodeString(value[comma+1:])
	if err != nil || len(raw) == 0 || len(raw) > maxIconBytes {
		return nil, fmt.Errorf("server icon must be a PNG, JPEG, or WebP image")
	}
	return raw, nil
}

func extForIcon(raw []byte) (string, error) {
	img, format, err := decodeIcon(raw)
	if err != nil {
		return "", fmt.Errorf("server icon must be a PNG, JPEG, or WebP image")
	}
	b := img.Bounds()
	if b.Dx() < 1 || b.Dy() < 1 || b.Dx() > maxIconEdge || b.Dy() > maxIconEdge {
		return "", fmt.Errorf("server icon must be a PNG, JPEG, or WebP image")
	}
	switch format {
	case "png":
		return ".png", nil
	case "jpeg":
		return ".jpg", nil
	case "webp":
		return ".webp", nil
	default:
		return "", fmt.Errorf("server icon must be a PNG, JPEG, or WebP image")
	}
}

func decodeIcon(raw []byte) (image.Image, string, error) {
	if len(raw) >= 12 && bytes.Equal(raw[:4], []byte("RIFF")) && bytes.Equal(raw[8:12], []byte("WEBP")) {
		img, err := webp.Decode(bytes.NewReader(raw))
		return img, "webp", err
	}
	img, format, err := image.Decode(bytes.NewReader(raw))
	if err != nil {
		return nil, "", err
	}
	if format != "png" && format != "jpeg" {
		return nil, "", fmt.Errorf("unsupported image")
	}
	return img, format, nil
}

// Register decoders. image/png and image/jpeg register via blank import side effects
// when their packages are imported for Encode, but Decode registration is init().
var (
	_ = png.Decode
	_ = jpeg.Decode
)
