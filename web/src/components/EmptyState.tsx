export function EmptyState({ sectionName }: { sectionName: string }) {
  return (
    <p className="rounded-xl border border-dashed border-line p-6 text-center text-muted">
      No {sectionName} news in the last 72 hours. Check back soon.
    </p>
  );
}
