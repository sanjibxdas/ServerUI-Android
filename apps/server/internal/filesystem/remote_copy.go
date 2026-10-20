package filesystem

import (
	"context"
	"fmt"
	"io"
	"os"
	"path"
	"strings"

	"github.com/pkg/sftp"
)

// Transfer copies a file or directory from one connected server to a directory
// on another. An empty toDir uses the destination account's home directory.
// removeSource deletes the original only after the copy succeeds.
func (s *Service) Transfer(ctx context.Context, fromID, toID, fromRaw, toDirRaw, toName string, overwrite, removeSource bool) error {
	fromID = strings.TrimSpace(fromID)
	toID = strings.TrimSpace(toID)
	if fromID == "" || toID == "" {
		return fmt.Errorf("server id is required")
	}
	if fromID == toID {
		return fmt.Errorf("use copy or move for the same server")
	}
	from, err := CleanPath(fromRaw)
	if err != nil {
		return err
	}
	if from == "/" {
		return fmt.Errorf("invalid path")
	}

	src, err := s.client(fromID)
	if err != nil {
		return err
	}
	defer src.Close()
	dst, err := s.client(toID)
	if err != nil {
		return err
	}
	defer dst.Close()

	toDir := strings.TrimSpace(toDirRaw)
	if toDir == "" || toDir == "~" {
		toDir, err = dst.Getwd()
		if err != nil {
			return mapFSError(err)
		}
	} else {
		toDir, err = CleanPath(toDir)
		if err != nil {
			return err
		}
	}
	name := path.Base(from)
	if strings.TrimSpace(toName) != "" {
		name = path.Base(strings.TrimSpace(toName))
		if name == "." || name == ".." || strings.ContainsAny(name, `/\`) {
			return fmt.Errorf("invalid name")
		}
	}
	target := path.Join(toDir, name)
	if err := copyAcross(ctx, src, dst, from, target, overwrite); err != nil {
		return err
	}
	if removeSource {
		if err := removeTree(src, from); err != nil {
			return fmt.Errorf("copied, but the original could not be removed: %w", err)
		}
	}
	return nil
}

func copyAcross(ctx context.Context, src, dst *sftp.Client, from, to string, overwrite bool) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	info, err := src.Lstat(from)
	if err != nil {
		return mapFSError(err)
	}
	if info.Mode()&os.ModeSymlink != 0 {
		return nil
	}
	if _, statErr := dst.Lstat(to); statErr == nil {
		if !overwrite {
			return ErrExists
		}
		if err := removeTree(dst, to); err != nil {
			return err
		}
	} else if !os.IsNotExist(statErr) && !isNotExistMessage(statErr) {
		return mapFSError(statErr)
	}
	if info.IsDir() {
		if err := dst.MkdirAll(to); err != nil {
			return mapFSError(err)
		}
		entries, err := src.ReadDir(from)
		if err != nil {
			return mapFSError(err)
		}
		for _, entry := range entries {
			name := entry.Name()
			if name == "." || name == ".." {
				continue
			}
			if err := copyAcross(ctx, src, dst, path.Join(from, name), path.Join(to, name), overwrite); err != nil {
				return err
			}
		}
		return nil
	}
	in, err := src.Open(from)
	if err != nil {
		return mapFSError(err)
	}
	defer in.Close()
	out, err := dst.Create(to)
	if err != nil {
		return mapFSError(err)
	}
	defer out.Close()
	if _, err := io.Copy(out, in); err != nil {
		return mapFSError(err)
	}
	return nil
}

func removeTree(client *sftp.Client, target string) error {
	info, err := client.Lstat(target)
	if err != nil {
		if os.IsNotExist(err) || isNotExistMessage(err) {
			return nil
		}
		return mapFSError(err)
	}
	if info.IsDir() {
		entries, err := client.ReadDir(target)
		if err != nil {
			return mapFSError(err)
		}
		for _, entry := range entries {
			name := entry.Name()
			if name == "." || name == ".." {
				continue
			}
			if err := removeTree(client, path.Join(target, name)); err != nil {
				return err
			}
		}
		if err := client.RemoveDirectory(target); err != nil {
			return mapFSError(err)
		}
		return nil
	}
	if err := client.Remove(target); err != nil {
		return mapFSError(err)
	}
	return nil
}

func isNotExistMessage(err error) bool {
	if err == nil {
		return false
	}
	msg := strings.ToLower(err.Error())
	return strings.Contains(msg, "not exist") || strings.Contains(msg, "no such file")
}
