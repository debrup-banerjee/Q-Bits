import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { WordsToKnow } from './WordsToKnow';

const TERMS = [
  { term: 'GPU', meaning: 'A chip that does many small sums at once.' },
  { term: 'Context window', meaning: 'How much text the AI can keep in mind.' },
];

function setWidth(wide: boolean) {
  window.matchMedia = ((query: string) => ({
    matches: wide,
    media: query,
    addEventListener: () => {},
    removeEventListener: () => {},
  })) as unknown as typeof window.matchMedia;
}

// 003 R6.2
it('starts collapsed on phones, showing only the term names', () => {
  setWidth(false);
  const { container } = render(<WordsToKnow terms={TERMS} />);

  expect(container.querySelector('details')).not.toHaveAttribute('open');
  expect(screen.getByText(': GPU, Context window')).toBeInTheDocument();
});

// 003 R6.2
it('opens with one tap', async () => {
  setWidth(false);
  const { container } = render(<WordsToKnow terms={TERMS} />);

  await userEvent.click(screen.getByText('Words to know'));

  expect(container.querySelector('details')).toHaveAttribute('open');
  expect(screen.getByText(/How much text the AI can keep in mind/)).toBeVisible();
});

// 003 R6.2
it('starts open on wider screens', () => {
  setWidth(true);
  const { container } = render(<WordsToKnow terms={TERMS} />);

  expect(container.querySelector('details')).toHaveAttribute('open');
});

// 003 R6.2
it('renders nothing without terms', () => {
  const { container } = render(<WordsToKnow terms={[]} />);
  expect(container).toBeEmptyDOMElement();
});
