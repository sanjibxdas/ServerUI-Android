package filesystem

import (
	"context"
	"testing"
)

func TestTransferRequiresBothServers(t *testing.T) {
	svc := New(nil)
	err := svc.Transfer(context.Background(), "", "other", "/tmp/a", "/tmp", "", false, false)
	if err == nil {
		t.Fatal("expected an error when the source server is missing")
	}
	err = svc.Transfer(context.Background(), "a", "a", "/tmp/a", "/tmp", "", false, false)
	if err == nil {
		t.Fatal("expected an error when both servers are the same")
	}
}
