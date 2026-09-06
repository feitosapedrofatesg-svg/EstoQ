import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import MudarPin from "./MudarPin";

const mocks = vi.hoisted(() => ({
  me: vi.fn(),
  putPin: vi.fn(),
  logout: vi.fn(),
}));

vi.mock("../api", () => ({
  api: { get: mocks.me, put: mocks.putPin },
}));

vi.mock("../auth", () => ({
  useAuth: () => ({ logout: mocks.logout }),
}));

vi.mock("../ux", () => ({
  useToast: () => ({ toast: vi.fn() }),
}));

describe("MudarPin", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("valida que os dois campos conferem", async () => {
    const user = userEvent.setup();
    render(<MudarPin />);

    await user.type(screen.getByLabelText("Novo PIN (6 dígitos)"), "123456");
    await user.type(screen.getByLabelText("Confirmar novo PIN"), "654321");
    await user.click(screen.getByRole("button", { name: "Salvar novo PIN" }));

    expect(screen.getByRole("alert")).toHaveTextContent("A confirmação não confere com o novo PIN.");
    expect(mocks.putPin).not.toHaveBeenCalled();
  });

  it("salva o novo PIN e desconecta o usuário", async () => {
    mocks.me.mockResolvedValue({ id: "abc" });
    mocks.putPin.mockResolvedValue({});
    const user = userEvent.setup();
    render(<MudarPin />);

    await user.type(screen.getByLabelText("Novo PIN (6 dígitos)"), "654321");
    await user.type(screen.getByLabelText("Confirmar novo PIN"), "654321");
    await user.click(screen.getByRole("button", { name: "Salvar novo PIN" }));

    expect(mocks.putPin).toHaveBeenCalledWith("/api/usuarios/abc/pin", { pin: "654321" });
    expect(mocks.logout).toHaveBeenCalled();
  });

  it("mantém o botão desabilitado até completar os 6 dígitos", async () => {
    const user = userEvent.setup();
    render(<MudarPin />);

    const button = screen.getByRole("button", { name: "Salvar novo PIN" });
    expect(button).toBeDisabled();

    await user.type(screen.getByLabelText("Novo PIN (6 dígitos)"), "123");
    await user.type(screen.getByLabelText("Confirmar novo PIN"), "123456");
    expect(button).toBeDisabled();

    await user.type(screen.getByLabelText("Novo PIN (6 dígitos)"), "456");
    expect(button).toBeEnabled();
  });
});