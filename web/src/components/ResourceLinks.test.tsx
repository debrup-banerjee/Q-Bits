import { render, screen, within } from '@testing-library/react';
import { ResourceLinks } from './ResourceLinks';

const RESOURCES = [
  {
    type: 'code',
    label: 'Code on GitHub',
    url: 'https://github.com/openai/whisper',
    name: 'openai/whisper',
  },
  {
    type: 'paper',
    label: 'Paper on arXiv',
    url: 'https://arxiv.org/abs/2410.01234',
    name: '2410.01234',
  },
];

it('lists each link with its label and name', () => {
  render(<ResourceLinks resources={RESOURCES} />);

  const group = screen.getByLabelText('Open source');
  const code = within(group).getByRole('link', {
    name: 'Code on GitHub: openai/whisper (opens in a new tab)',
  });
  expect(code).toHaveAttribute('href', 'https://github.com/openai/whisper');
  expect(code).toHaveAttribute('target', '_blank');
  expect(code).toHaveAttribute('rel', 'noopener noreferrer');
  expect(within(code).getByText('openai/whisper').tagName).toBe('CODE');
  expect(
    within(group).getByRole('link', { name: /Paper on arXiv: 2410.01234/ }),
  ).toBeInTheDocument();
});

it('renders nothing when there are no links', () => {
  const { container } = render(<ResourceLinks resources={[]} />);
  expect(container).toBeEmptyDOMElement();
});
